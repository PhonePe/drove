/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.controller.engine.jobs;

import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.model.executor.StartTaskMessage;
import com.phonepe.drove.common.net.HttpCaller;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.ControllerRetrySpecFactory;
import com.phonepe.drove.controller.engine.InstanceIdGenerator;
import com.phonepe.drove.controller.resourcemgmt.InstanceScheduler;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.jobexecutor.Job;
import com.phonepe.drove.jobexecutor.JobContext;
import com.phonepe.drove.jobexecutor.JobResponseCombiner;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.task.TaskSpec;
import dev.failsafe.Failsafe;
import dev.failsafe.TimeoutExceededException;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Date;
import java.util.List;

import static com.phonepe.drove.common.CommonUtils.waitForAction;
import static com.phonepe.drove.controller.utils.ControllerUtils.translateConfigSpecs;

/**
 * Starts  a single instance by whatever means necessary
 */
@Slf4j
public class StartTaskJob implements Job<Boolean> {
    private final TaskSpec taskSpec;
    private final ClusterOpSpec clusterOpSpec;
    private final InstanceScheduler scheduler;
    private final TaskDB taskDB;
    private final ControllerCommunicator communicator;
    private final String schedulingSessionId;
    private final ControllerRetrySpecFactory retrySpecFactory;

    private final InstanceIdGenerator instanceIdGenerator;
    private final HttpCaller httpCaller;

    @SuppressWarnings("java:S107")
    public StartTaskJob(
            TaskSpec taskSpec,
            ClusterOpSpec clusterOpSpec,
            InstanceScheduler scheduler,
            TaskDB taskDB,
            ControllerCommunicator communicator,
            String schedulingSessionId,
            ControllerRetrySpecFactory retrySpecFactory,
            InstanceIdGenerator instanceIdGenerator,
            HttpCaller httpCaller) {
        this.taskSpec = taskSpec;
        this.clusterOpSpec = clusterOpSpec;
        this.scheduler = scheduler;
        this.taskDB = taskDB;
        this.communicator = communicator;
        this.schedulingSessionId = schedulingSessionId;
        this.retrySpecFactory = retrySpecFactory;
        this.instanceIdGenerator = instanceIdGenerator;
        this.httpCaller = httpCaller;
    }


    @Override
    public String jobId() {
        return "start-task-" + ControllerUtils.deployableObjectId(taskSpec) + "-" + new Date().getTime();
    }

    @Override
    public void cancel() {
        //Nothing to do here
    }

    @Override
    @MonitoredFunction
    public Boolean execute(JobContext<Boolean> context, JobResponseCombiner<Boolean> responseCombiner) {
        val sourceApp = taskSpec.getSourceAppName();
        val taskId = taskSpec.getTaskId();
        val retryPolicy = CommonUtils.<Boolean>policy(
                retrySpecFactory.jobRetrySpec(ControllerUtils.maxStartTimeout(taskSpec)),
                instanceScheduled -> !context.isCancelled() && !context.isStopped() && !instanceScheduled);
        val instanceId = instanceIdGenerator.generate(this.taskSpec);

        try {
            val status = waitForAction(retryPolicy,
                                       () -> startInstance(taskSpec, instanceId),
                                       event -> {
                                           val failure = event.getException();
                                           if (null != failure) {
                                               log.error("Error setting up task for " + sourceApp + "/" + taskId,
                                                         failure);
                                           }
                                           else {
                                               log.error("Error setting up task for {}/{}. Event: {}",
                                                         sourceApp,
                                                         taskId,
                                                         event);
                                           }
                                       });

            if (context.isStopped() || context.isCancelled()) {
                return false;
            }
            return status;
        }
        catch (TimeoutExceededException e) {
            log.error("Could not allocate a task for {}/{} after retires.", sourceApp, taskId);
        }
        catch (Exception e) {
            log.error("Could not allocate an instance for " + sourceApp + "/" + taskId + " after retires.", e);
        }
        return false;
    }

    @SuppressWarnings("java:S1874")
    private boolean ensureDataAvailability() {
        val retryPolicy =
                CommonUtils.<Boolean>policy(
                        retrySpecFactory.instanceStateCheckRetrySpec(clusterOpSpec.getTimeout().toMilliseconds()),
                        r -> !r);
        try {
            return Failsafe.with(List.of(retryPolicy))
                    .get(() -> taskDB.task(taskSpec.getSourceAppName(), taskSpec.getTaskId()).isPresent());
        }
        catch (Exception e) {
            log.error("Could not start task: ", e);
            return false;
        }
    }

    private boolean startInstance(TaskSpec taskSpec, String instanceId) {
        val sourceApp = taskSpec.getSourceAppName();
        val taskId = taskSpec.getTaskId();
        val node = scheduler.schedule(schedulingSessionId, instanceId, taskSpec)
                .orElse(null);
        if (null == node) {
            log.warn("No node found in the cluster that can provide required resources" +
                             " and satisfy the placement policy needed for {}/{}.",
                     sourceApp, taskId);
            return false;
        }

        val startMessage = new StartTaskMessage(MessageHeader.controllerRequest(),
                                                new ExecutorAddress(node.getExecutorId(),
                                                                    node.getHostname(),
                                                                    node.getPort(),
                                                                    node.getTransportType()),
                                                new TaskInstanceSpec(taskId,
                                                                     sourceApp,
                                                                     instanceId,
                                                                     taskSpec.getExecutable(),
                                                                     List.of(node.getCpu(),
                                                                             node.getMemory()),
                                                                     taskSpec.getVolumes(),
                                                                     translateConfigSpecs(taskSpec.getConfigs(),
                                                                                          httpCaller),
                                                                     taskSpec.getLogging(),
                                                                     taskSpec.getEnv(),
                                                                     taskSpec.getArgs(),
                                                                     taskSpec.getDevices()));
        var successful = false;
        try {
            val response = communicator.send(startMessage);
            log.trace("Sent message to start task: {}/{}. Response: {} Message: {}",
                      sourceApp,
                      taskId,
                      response,
                      startMessage);
            if (!response.getStatus().equals(MessageDeliveryStatus.ACCEPTED)) {
                log.warn("Sending start message failed with status: {} for {}/{}. Rescheduling necessary.",
                         response.getStatus(), sourceApp, taskId);
            }
            else {
                log.info("Start message for instance {}/{} accepted by executor {}",
                         sourceApp, taskId, node.getExecutorId());
                successful = ensureDataAvailability();
            }
        }
        finally {
            if (!successful) {
                log.warn("Instance could not be started. Deallocating resources on node: {}", node.getExecutorId());
                scheduler.discardAllocation(schedulingSessionId, instanceId, node);
            }
        }
        return successful;
    }
}
