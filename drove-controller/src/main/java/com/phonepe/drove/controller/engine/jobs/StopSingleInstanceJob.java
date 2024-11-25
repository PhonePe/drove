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

import com.google.common.base.Strings;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.model.executor.StopInstanceMessage;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.ControllerRetrySpecFactory;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.resourcemgmt.InstanceScheduler;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.jobexecutor.Job;
import com.phonepe.drove.jobexecutor.JobContext;
import com.phonepe.drove.jobexecutor.JobResponseCombiner;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import dev.failsafe.TimeoutExceededException;

import static com.phonepe.drove.controller.utils.ControllerUtils.ensureInstanceState;
import static com.phonepe.drove.common.CommonUtils.waitForAction;

/**
 * Starts  a single instance by whatever means necessary
 */
@Slf4j
public class StopSingleInstanceJob implements Job<Boolean> {
    private final String appId;
    private final String instanceId;
    private final ClusterOpSpec clusterOpSpec;
    private final InstanceScheduler scheduler;
    private final String schedulingSessionId;
    private final ApplicationInstanceInfoDB instanceInfoDB;
    private final ClusterResourcesDB clusterResourcesDB;
    private final ControllerCommunicator communicator;
    private final ControllerRetrySpecFactory retrySpecFactory;

    @SuppressWarnings("java:S107")
    public StopSingleInstanceJob(
            String appId,
            String instanceId,
            ClusterOpSpec clusterOpSpec,
            InstanceScheduler scheduler,
            String schedulingSessionId,
            ApplicationInstanceInfoDB instanceInfoDB, ClusterResourcesDB clusterResourcesDB,
            ControllerCommunicator communicator,
            ControllerRetrySpecFactory retrySpecFactory) {
        this.appId = appId;
        this.instanceId = instanceId;
        this.clusterOpSpec = clusterOpSpec;
        this.scheduler = scheduler;
        this.schedulingSessionId = schedulingSessionId;
        this.instanceInfoDB = instanceInfoDB;
        this.clusterResourcesDB = clusterResourcesDB;
        this.communicator = communicator;
        this.retrySpecFactory = retrySpecFactory;
    }

    @Override
    public String jobId() {
        return "stop-instance-" + appId + "-" + instanceId;
    }

    @Override
    public void cancel() {
        //This job type cannot be cancelled
    }

    @Override
    @MonitoredFunction
    public Boolean execute(JobContext<Boolean> context, JobResponseCombiner<Boolean> responseCombiner) {
        val retryPolicy = CommonUtils.<Boolean>policy(retrySpecFactory.jobRetrySpec(), r -> !r);
        val instanceInfo = instanceInfoDB.instance(appId, instanceId).orElse(null);
        if (null == instanceInfo) {
            log.warn("No instance found for {}/{}", appId, instanceId);
            return true;
        }
        try {
            return waitForAction(retryPolicy,
                                 () -> stopInstance(instanceInfo, clusterOpSpec),
                                 event -> {
                                     val failure = event.getException();
                                     if (null != failure) {
                                         log.error("Error stopping instance for " + appId, failure);
                                     }
                                     else {
                                         log.error("Error stopping instance for {}. Event: {}", appId, event);
                                     }
                                 });
        }
        catch (TimeoutExceededException e) {
            log.error("Could not stop an instance for {} after retires.", appId);
        }
        catch (Exception e) {
            log.error("Could not stop an instance for " + appId + " after retires.", e);
        }
        return false;
    }

    private boolean stopInstance(
            final InstanceInfo instanceInfo, final ClusterOpSpec clusterOpSpec) {
        val executorId = instanceInfo.getExecutorId();
        val node = clusterResourcesDB.currentSnapshot(executorId)
                .map(ExecutorHostInfo::getNodeData)
                .orElse(null);
        if (null == node) {
            log.warn("No node found in the cluster with ID {}.", executorId);
            return false;
        }
        val stopMessage = new StopInstanceMessage(MessageHeader.controllerRequest(),
                                                   new ExecutorAddress(executorId,
                                                                       instanceInfo.getLocalInfo().getHostname(),
                                                                       node.getPort(),
                                                                       node.getTransportType()),
                                                   instanceId);
        val response = communicator.send(stopMessage);
        log.trace("Sent message to stop instance: {}/{}. Message: {}", appId, instanceId, stopMessage);
        if(!response.getStatus().equals(MessageDeliveryStatus.ACCEPTED)) {
            log.warn("Instance {} could not be stopped. Sending message failed: {}", instanceId, executorId);
            return false;
        }
        val stopped = ensureInstanceState(instanceInfoDB, clusterOpSpec, appId, instanceId, InstanceState.STOPPED, retrySpecFactory);
        if(stopped && !Strings.isNullOrEmpty(schedulingSessionId)) {
            scheduler.discardAllocation(schedulingSessionId, instanceId, null);
        }
        return stopped;
    }

}
