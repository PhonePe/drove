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

import com.phonepe.drove.auth.core.ApplicationInstanceTokenManager;
import com.phonepe.drove.auth.model.DroveApplicationInstanceInfo;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.LocalServiceInstanceSpec;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.model.executor.StartLocalServiceInstanceMessage;
import com.phonepe.drove.common.net.HttpCaller;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.ControllerRetrySpecFactory;
import com.phonepe.drove.controller.engine.InstanceIdGenerator;
import com.phonepe.drove.controller.resourcemgmt.AllocatedExecutorNode;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.resourcemgmt.InstanceScheduler;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.jobexecutor.Job;
import com.phonepe.drove.jobexecutor.JobContext;
import com.phonepe.drove.jobexecutor.JobResponseCombiner;
import com.phonepe.drove.models.application.placement.policies.*;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceSpec;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import dev.failsafe.TimeoutExceededException;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Date;
import java.util.EnumSet;
import java.util.List;

import static com.phonepe.drove.common.CommonUtils.waitForAction;
import static com.phonepe.drove.controller.utils.ControllerUtils.*;

/**
 * Starts  a single instance by whatever means necessary
 */
@Slf4j
public class StartSingleLocalServiceInstanceJob implements Job<Boolean> {
    private final LocalServiceInfo localServiceInfo;
    private final ClusterOpSpec clusterOpSpec;
    private final InstanceScheduler scheduler;
    private final LocalServiceStateDB instanceInfoDB;
    private final ControllerCommunicator communicator;
    private final String schedulingSessionId;
    private final ControllerRetrySpecFactory retrySpecFactory;

    private final InstanceIdGenerator instanceIdGenerator;

    private final ApplicationInstanceTokenManager tokenManager;
    private final HttpCaller httpCaller;
    private final ExecutorHostInfo executor;

    @SuppressWarnings("java:S107")
    public StartSingleLocalServiceInstanceJob(
            LocalServiceInfo localServiceInfo,
            ClusterOpSpec clusterOpSpec,
            InstanceScheduler scheduler,
            LocalServiceStateDB instanceInfoDB,
            ControllerCommunicator communicator,
            String schedulingSessionId,
            ControllerRetrySpecFactory retrySpecFactory,
            InstanceIdGenerator instanceIdGenerator,
            ApplicationInstanceTokenManager tokenManager,
            HttpCaller httpCaller,
            ExecutorHostInfo executor) {
        this.localServiceInfo = localServiceInfo;
        this.clusterOpSpec = clusterOpSpec;
        this.scheduler = scheduler;
        this.instanceInfoDB = instanceInfoDB;
        this.communicator = communicator;
        this.schedulingSessionId = schedulingSessionId;
        this.retrySpecFactory = retrySpecFactory;
        this.instanceIdGenerator = instanceIdGenerator;
        this.tokenManager = tokenManager;
        this.httpCaller = httpCaller;
        this.executor = executor;
    }

    @Override
    public String jobId() {
        return "start-instance-" + ControllerUtils.deployableObjectId(localServiceInfo.getSpec()) + "-" + new Date().getTime();
    }

    @Override
    public void cancel() {
        //Nothing to do here
    }

    @Override
    @MonitoredFunction
    public Boolean execute(JobContext<Boolean> context, JobResponseCombiner<Boolean> responseCombiner) {
        val retryPolicy = CommonUtils.<Boolean>policy(retrySpecFactory.jobRetrySpec(),
                                                      instanceScheduled -> !context.isCancelled() && !context.isStopped() && !instanceScheduled);
        val serviceId = ControllerUtils.deployableObjectId(localServiceInfo.getSpec());
        try {
            val status = waitForAction(retryPolicy,
                                       () -> startInstance(localServiceInfo.getSpec(), clusterOpSpec),
                                       event -> {
                                           val failure = event.getException();
                                           if (null != failure) {
                                               log.error("Error setting up instance for " + serviceId, failure);
                                           }
                                           else {
                                               log.error("Error setting up instance for {}. Event: {}",
                                                         serviceId,
                                                         event);
                                           }
                                       });
            if (context.isStopped() || context.isCancelled()) {
                return false;
            }
            return status;
        }
        catch (TimeoutExceededException e) {
            log.error("Could not allocate an instance for {} after retires.", serviceId);
        }
        catch (Exception e) {
            log.error("Could not allocate an instance for " + serviceId + " after retires.", e);
        }
        return false;
    }

    private boolean startInstance(LocalServiceSpec localServiceSpec, ClusterOpSpec clusterOpSpec) {
        val serviceId = ControllerUtils.deployableObjectId(localServiceSpec);
        val instanceId = instanceIdGenerator.generate(localServiceSpec);
        val node = scheduler.schedule(
                        schedulingSessionId, instanceId, localServiceSpec,
                        new MatchTagPlacementPolicy(executor.getNodeData().getHostname()),
                        EnumSet.of(ExecutorState.ACTIVE, ExecutorState.UNREADY))
                .orElse(null);
        if (null == node) {
            log.warn("No node found in the cluster that can provide required resources" +
                             " and satisfy the placement policy needed for {}.",
                     ControllerUtils.deployableObjectId(localServiceSpec));
            return false;
        }

        val spec = new LocalServiceInstanceSpec(serviceId,
                                                localServiceSpec.getName(),
                                                instanceId,
                                                localServiceSpec.getExecutable(),
                                                List.of(node.getCpu(), node.getMemory()),
                                                localServiceSpec.getExposedPorts(),
                                                isHostLevelDeployable(localServiceSpec.getPlacementPolicy()),
                                                localServiceSpec.getVolumes(),
                                                translateConfigSpecs(localServiceSpec.getConfigs(), httpCaller),
                                                localServiceSpec.getHealthcheck(),
                                                localServiceSpec.getReadiness(),
                                                localServiceSpec.getLogging(),
                                                localServiceSpec.getEnv(),
                                                localServiceSpec.getArgs(),
                                                localServiceSpec.getDevices(),
                                                localServiceSpec.getPreShutdown(),
                                                generateAppInstanceToken(node, serviceId, instanceId));
        val startMessage = new StartLocalServiceInstanceMessage(MessageHeader.controllerRequest(),
                                                                new ExecutorAddress(node.getExecutorId(),
                                                                                    node.getHostname(),
                                                                                    node.getPort(),
                                                                                    node.getTransportType()),
                                                                spec);
        var successful = false;
        try {
            val response = communicator.send(startMessage);
            log.trace("Sent message to start instance: {}/{}. Response: {} Message: {}",
                      serviceId,
                      instanceId,
                      response,
                      startMessage);
            if (!response.getStatus().equals(MessageDeliveryStatus.ACCEPTED)) {
                log.warn("Sending start message failed with status: {} for {}. Rescheduling necessary.",
                         response.getStatus(), instanceId);
            }
            else {
                log.info("Start message for instance {}/{} accepted by executor {}",
                         serviceId, instanceId, node.getExecutorId());
                successful = ensureInstanceState(instanceInfoDB,
                                                 ControllerUtils.computeTimeout(clusterOpSpec, spec),
                                                 serviceId,
                                                 instanceId,
                                                 LocalServiceInstanceState.HEALTHY,
                                                 retrySpecFactory);
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

    private String generateAppInstanceToken(AllocatedExecutorNode node, String serviceId, String instanceId) {
        return tokenManager.generate(new DroveApplicationInstanceInfo(
                serviceId,
                instanceId,
                node.getExecutorId())).orElse(null);
    }

}
