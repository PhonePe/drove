package com.phonepe.drove.controller.engine.jobs;

import com.phonepe.drove.auth.core.ApplicationInstanceTokenManager;
import com.phonepe.drove.auth.model.DroveApplicationInstanceInfo;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.model.executor.StartInstanceMessage;
import com.phonepe.drove.common.net.HttpCaller;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.ControllerRetrySpecFactory;
import com.phonepe.drove.controller.engine.InstanceIdGenerator;
import com.phonepe.drove.controller.resourcemgmt.AllocatedExecutorNode;
import com.phonepe.drove.controller.resourcemgmt.InstanceScheduler;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.jobexecutor.Job;
import com.phonepe.drove.jobexecutor.JobContext;
import com.phonepe.drove.jobexecutor.JobResponseCombiner;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.TimeoutExceededException;

import java.util.Date;
import java.util.List;

import static com.phonepe.drove.controller.utils.ControllerUtils.ensureInstanceState;
import static com.phonepe.drove.controller.utils.ControllerUtils.translateConfigSpecs;

/**
 * Starts  a single instance by whatever means necessary
 */
@Slf4j
public class StartSingleInstanceJob implements Job<Boolean> {
    private final ApplicationSpec applicationSpec;
    private final ClusterOpSpec clusterOpSpec;
    private final InstanceScheduler scheduler;
    private final ApplicationInstanceInfoDB instanceInfoDB;
    private final ControllerCommunicator communicator;
    private final String schedulingSessionId;
    private final ControllerRetrySpecFactory retrySpecFactory;

    private final InstanceIdGenerator instanceIdGenerator;

    private final ApplicationInstanceTokenManager tokenManager;
    private final HttpCaller httpCaller;

    @SuppressWarnings("java:S107")
    public StartSingleInstanceJob(
            ApplicationSpec applicationSpec,
            ClusterOpSpec clusterOpSpec,
            InstanceScheduler scheduler,
            ApplicationInstanceInfoDB instanceInfoDB,
            ControllerCommunicator communicator,
            String schedulingSessionId,
            ControllerRetrySpecFactory retrySpecFactory,
            InstanceIdGenerator instanceIdGenerator,
            ApplicationInstanceTokenManager tokenManager,
            HttpCaller httpCaller) {
        this.applicationSpec = applicationSpec;
        this.clusterOpSpec = clusterOpSpec;
        this.scheduler = scheduler;
        this.instanceInfoDB = instanceInfoDB;
        this.communicator = communicator;
        this.schedulingSessionId = schedulingSessionId;
        this.retrySpecFactory = retrySpecFactory;
        this.instanceIdGenerator = instanceIdGenerator;
        this.tokenManager = tokenManager;
        this.httpCaller = httpCaller;
    }

    @Override
    public String jobId() {
        return "start-instance-" + ControllerUtils.deployableObjectId(applicationSpec) + "-" + new Date().getTime();
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
        val appId = ControllerUtils.deployableObjectId(applicationSpec);
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
                     ControllerUtils.deployableObjectId(applicationSpec));
            return false;
        }
        val appId = ControllerUtils.deployableObjectId(applicationSpec);
        val instanceId = instanceIdGenerator.generate(applicationSpec);
        val startMessage = new StartInstanceMessage(MessageHeader.controllerRequest(),
                                                    new ExecutorAddress(node.getExecutorId(),
                                                                        node.getHostname(),
                                                                        node.getPort(),
                                                                        node.getTransportType()),
                                                    new ApplicationInstanceSpec(appId,
                                                                                applicationSpec.getName(),
                                                                                instanceId,
                                                                                applicationSpec.getExecutable(),
                                                                                List.of(node.getCpu(),
                                                                                        node.getMemory()),
                                                                                applicationSpec.getExposedPorts(),
                                                                                applicationSpec.getVolumes(),
                                                                                translateConfigSpecs(applicationSpec.getConfigs(),
                                                                                                     httpCaller),
                                                                                applicationSpec.getHealthcheck(),
                                                                                applicationSpec.getReadiness(),
                                                                                applicationSpec.getLogging(),
                                                                                applicationSpec.getEnv(),
                                                                                applicationSpec.getPreShutdown(),
                                                                                generateAppInstanceToken(node,
                                                                                                         appId,
                                                                                                         instanceId)));
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
                successful = ensureInstanceState(instanceInfoDB,
                                                 clusterOpSpec,
                                                 appId,
                                                 instanceId,
                                                 InstanceState.HEALTHY,
                                                 retrySpecFactory);
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

    private String generateAppInstanceToken(AllocatedExecutorNode node, String appId, String instanceId) {
        return tokenManager.generate(new DroveApplicationInstanceInfo(
                appId,
                instanceId,
                node.getExecutorId())).orElse(null);
    }

}
