package com.phonepe.drove.controller.engine.jobs;

import com.phonepe.drove.common.model.InstanceSpec;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.model.executor.StartInstanceMessage;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.jobexecutor.Job;
import com.phonepe.drove.controller.jobexecutor.JobContext;
import com.phonepe.drove.controller.jobexecutor.JobResponseCombiner;
import com.phonepe.drove.controller.resourcemgmt.InstanceScheduler;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.TimeoutExceededException;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.phonepe.drove.controller.utils.ControllerUtils.ensureInstanceState;

/**
 * Starts  a single instance by whatever means necessary
 */
@Slf4j
public class StartSingleInstanceJob implements Job<Boolean> {
    private final ApplicationSpec applicationSpec;
    private final ClusterOpSpec clusterOpSpec;
    private final InstanceScheduler scheduler;
    private final ApplicationStateDB applicationStateDB;
    private final ControllerCommunicator communicator;
    private final String schedulingSessionId;

    public StartSingleInstanceJob(
            ApplicationSpec applicationSpec,
            ClusterOpSpec clusterOpSpec,
            InstanceScheduler scheduler,
            ApplicationStateDB applicationStateDB,
            ControllerCommunicator communicator, String schedulingSessionId) {
        this.applicationSpec = applicationSpec;
        this.clusterOpSpec = clusterOpSpec;
        this.scheduler = scheduler;
        this.applicationStateDB = applicationStateDB;
        this.communicator = communicator;
        this.schedulingSessionId = schedulingSessionId;
    }

    @Override
    public String jobId() {
        return "start-instance-" + ControllerUtils.appId(applicationSpec) + "-" + new Date().getTime();
    }

    @Override
    public void cancel() {

    }

    @Override
    @MonitoredFunction
    public Boolean execute(JobContext<Boolean> context, JobResponseCombiner<Boolean> responseCombiner) {
        val retryPolicy = new RetryPolicy<Boolean>()
                .withDelay(Duration.ofSeconds(30))
                .withMaxDuration(Duration.ofMinutes(3))
                .handle(Exception.class)
                .handleResultIf(instanceScheduled -> !context.isCancelled() && !context.isStopped() && !instanceScheduled);
        val appId = ControllerUtils.appId(applicationSpec);
        try {
            val status = Failsafe.with(retryPolicy)
                    .onFailure(event -> {
                        val failure = event.getFailure();
                        if (null != failure) {
                            log.error("Error setting up instance for " + appId, failure);
                        }
                        else {
                            log.error("Error setting up instance for {}. Event: {}", appId, event);
                        }
                    })
                    .get(() -> startInstance(applicationSpec, clusterOpSpec));
            if (context.isStopped() || context.isCancelled()) {
                return false;
            }
            return status;
        }
        catch (TimeoutExceededException e) {
            log.error("Could not allocate an instance for {} after retires.", appId);
        }
        catch (Exception e) {
            log.error("Could not allocate an instance for " + appId + " after retires.", e);
        }
        return false;
    }

    private boolean startInstance(ApplicationSpec applicationSpec, ClusterOpSpec clusterOpSpec) {
        val node = scheduler.schedule(schedulingSessionId, applicationSpec)
                .orElse(null);
        if (null == node) {
            log.warn("No node found in the cluster that can provide required resources" +
                             " and satisfy the placement policy needed for {}.",
                     ControllerUtils.appId(applicationSpec));
            return false;
        }
        val appId = ControllerUtils.appId(applicationSpec);
        val instanceId = UUID.randomUUID().toString();
        val startMessage = new StartInstanceMessage(MessageHeader.controllerRequest(),
                                                    new ExecutorAddress(node.getExecutorId(),
                                                                        node.getHostname(),
                                                                        node.getPort()),
                                                    new InstanceSpec(appId,
                                                                     applicationSpec.getName(),
                                                                     instanceId,
                                                                     applicationSpec.getExecutable(),
                                                                     List.of(node.getCpu(),
                                                                             node.getMemory()),
                                                                     applicationSpec.getExposedPorts(),
                                                                     applicationSpec.getVolumes(),
                                                                     applicationSpec.getHealthcheck(),
                                                                     applicationSpec.getReadiness(),
                                                                     applicationSpec.getEnv()));
        var successful = false;
        try {
            val response = communicator.send(startMessage);
            log.trace("Sent message to start instance: {}/{}. Response: {} Message: {}",
                      appId,
                      instanceId,
                      response,
                      startMessage);
            if (!response.getStatus().equals(MessageDeliveryStatus.ACCEPTED)) {
                log.warn("Sending start message failed with status: {} for {}. Rescheduling necessary.",
                         response.getStatus(), instanceId);
            }
            else {
                log.info("Start message for instance {}/{} accepted by executor {}",
                         appId, instanceId, node.getExecutorId());
                successful = ensureInstanceState(applicationStateDB,
                                                 clusterOpSpec,
                                                 appId,
                                                 instanceId,
                                                 InstanceState.HEALTHY);
            }
        }
        finally {
            if (!successful) {
                log.warn("Instance could not be started. Deallocating resources on node: {}", node.getExecutorId());
                scheduler.discardAllocation(schedulingSessionId, node);
            }
        }
        return successful;
        //TODO::IDENTIFY THE STATES AND DO NOT GIVE UP QUICKLY IF IT IS IN STARTING STATE ETC
    }

}
