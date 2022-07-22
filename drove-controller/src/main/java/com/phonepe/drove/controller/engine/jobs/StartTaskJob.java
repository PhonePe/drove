package com.phonepe.drove.controller.engine.jobs;

import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.model.executor.StartTaskInstanceMessage;
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
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.TimeoutExceededException;

import java.util.Date;
import java.util.List;

import static com.phonepe.drove.common.model.MessageDeliveryStatus.ACCEPTED;

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

    public StartTaskJob(
            TaskSpec taskSpec,
            ClusterOpSpec clusterOpSpec,
            InstanceScheduler scheduler,
            TaskDB taskDB,
            ControllerCommunicator communicator,
            String schedulingSessionId,
            ControllerRetrySpecFactory retrySpecFactory,
            InstanceIdGenerator instanceIdGenerator) {
        this.taskSpec = taskSpec;
        this.clusterOpSpec = clusterOpSpec;
        this.scheduler = scheduler;
        this.taskDB = taskDB;
        this.communicator = communicator;
        this.schedulingSessionId = schedulingSessionId;
        this.retrySpecFactory = retrySpecFactory;
        this.instanceIdGenerator = instanceIdGenerator;
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
        val sourceApp = taskSpec.getSourceApp();
        val taskId = taskSpec.getTaskId();
        val retryPolicy = CommonUtils.<Boolean>policy(retrySpecFactory.jobStartRetrySpec(),
                                                                    instanceScheduled -> !context.isCancelled() && !context.isStopped() && !instanceScheduled);
        val instanceId = instanceIdGenerator.generate(this.taskSpec);

        try {
            val status = Failsafe.with(retryPolicy)
                    .onFailure(event -> {
                        val failure = event.getFailure();
                        if (null != failure) {
                            log.error("Error setting up task for " + sourceApp + "/" + taskId, failure);
                        }
                        else {
                            log.error("Error setting up task for {}/{}. Event: {}", sourceApp, taskId, event);
                        }
                    })
                    .get(() -> startInstance(taskSpec, instanceId, clusterOpSpec));

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

    private MessageDeliveryStatus sendStartMessage(TaskSpec taskSpec, String instanceId) {
        val sourceApp = taskSpec.getSourceApp();
        val taskId = taskSpec.getTaskId();
        val node = scheduler.schedule(schedulingSessionId, taskSpec)
                .orElse(null);
        if (null != node) {

            val startMessage = new StartTaskInstanceMessage(MessageHeader.controllerRequest(),
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
                                                                                 taskSpec.getLogging(),
                                                                                 taskSpec.getEnv()));
            var successful = false;
            try {
                val response = communicator.send(startMessage);
                val status = response.getStatus();
                if (ACCEPTED.equals(status)) {
                    log.info("Start message for instance {}/{} accepted by executor {}",
                             taskSpec.getSourceApp(), taskSpec.getTaskId(), node.getExecutorId());
                }
                else {
                    log.info(
                            "Start message for instance {}/{} was not accepted by executor {}. Response: {}. Message:" +
                                    " {}",
                            taskSpec.getSourceApp(),
                            taskSpec.getTaskId(),
                            node.getExecutorId(),
                            response,
                            startMessage);
                }
                return status;
            }
            catch (Exception e) {
                log.error("Error sending message for {}/{}", sourceApp, taskId);
            }
        }
        else {
            log.warn("No node found in the cluster that can provide required resources" +
                             " and satisfy the placement policy needed for {}/{}.", sourceApp, taskId);
        }
        return MessageDeliveryStatus.FAILED;
    }

    private boolean ensureDataAvailability() {
        val retryPolicy =
                CommonUtils.<Boolean>policy(
                        retrySpecFactory.instanceStateCheckRetrySpec(clusterOpSpec.getTimeout().toMilliseconds()),
                        r -> !r);
        try {
            return Failsafe.with(List.of(retryPolicy))
                    .get(() -> taskDB.task(taskSpec.getSourceApp(), taskSpec.getTaskId()).isPresent());
        }
        catch (Exception e) {
            log.error("Could not start task: ", e);
            return false;
        }
    }

    private boolean startInstance(TaskSpec taskSpec, String instanceId, ClusterOpSpec clusterOpSpec) {
        val sourceApp = taskSpec.getSourceApp();
        val taskId = taskSpec.getTaskId();
        val node = scheduler.schedule(schedulingSessionId, taskSpec)
                .orElse(null);
        if (null == node) {
            log.warn("No node found in the cluster that can provide required resources" +
                             " and satisfy the placement policy needed for {}/{}.",
                     sourceApp, taskId);
            return false;
        }

        val startMessage = new StartTaskInstanceMessage(MessageHeader.controllerRequest(),
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
                                                                             taskSpec.getLogging(),
                                                                             taskSpec.getEnv()));
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
                scheduler.discardAllocation(schedulingSessionId, node);
            }
        }
        return successful;
    }
}
