package com.phonepe.drove.controller.engine.jobs;

import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.model.executor.StopInstanceMessage;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.ControllerRetrySpecFactory;
import com.phonepe.drove.jobexecutor.Job;
import com.phonepe.drove.jobexecutor.JobContext;
import com.phonepe.drove.jobexecutor.JobResponseCombiner;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.TimeoutExceededException;

import static com.phonepe.drove.controller.utils.ControllerUtils.ensureInstanceState;

/**
 * Starts  a single instance by whatever means necessary
 */
@Slf4j
public class StopSingleInstanceJob implements Job<Boolean> {
    private final String appId;
    private final String instanceId;
    private final ClusterOpSpec clusterOpSpec;
    private final ApplicationInstanceInfoDB instanceInfoDB;
    private final ClusterResourcesDB clusterResourcesDB;
    private final ControllerCommunicator communicator;
    private final ControllerRetrySpecFactory retrySpecFactory;

    public StopSingleInstanceJob(
            String appId,
            String instanceId,
            ClusterOpSpec clusterOpSpec,
            ApplicationInstanceInfoDB instanceInfoDB, ClusterResourcesDB clusterResourcesDB,
            ControllerCommunicator communicator,
            ControllerRetrySpecFactory retrySpecFactory) {
        this.appId = appId;
        this.instanceId = instanceId;
        this.clusterOpSpec = clusterOpSpec;
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
        val retryPolicy = CommonUtils.<Boolean>policy(retrySpecFactory.jobStartRetrySpec(), r -> !r);
        val instanceInfo = instanceInfoDB.instance(appId, instanceId).orElse(null);
        if (null == instanceInfo) {
            log.warn("No instance found for {}/{}", appId, instanceId);
            return true;
        }
        try {
            return Failsafe.with(retryPolicy)
                    .onFailure(event -> {
                        val failure = event.getFailure();
                        if (null != failure) {
                            log.error("Error stopping instance for " + appId, failure);
                        }
                        else {
                            log.error("Error stopping instance for {}. Event: {}", appId, event);
                        }
                    })
                    .get(() -> stopInstance(instanceInfo, clusterOpSpec));
        }
        catch (TimeoutExceededException e) {
            log.error("Could not stop an instance for {} after retires.", appId);
        }
        catch (Exception e) {
            log.error("Could not stop an instance for " + appId + " after retires.", e);
        }
        return false;
    }

    private boolean stopInstance(final InstanceInfo instanceInfo, final ClusterOpSpec clusterOpSpec) {
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
        log.trace("Sent message to start instance: {}/{}. Message: {}", appId, instanceId, stopMessage);
        if(!response.getStatus().equals(MessageDeliveryStatus.ACCEPTED)) {
            log.warn("Instance {} could not be stopped. Sending message failed: {}", instanceId, executorId);
            return false;
        }
        return ensureInstanceState(instanceInfoDB, clusterOpSpec, appId, instanceId, InstanceState.STOPPED, retrySpecFactory);
    }

}
