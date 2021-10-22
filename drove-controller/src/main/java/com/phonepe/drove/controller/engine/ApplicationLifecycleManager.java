package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.model.InstanceSpec;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.model.executor.StartInstanceMessage;
import com.phonepe.drove.controller.resources.InstanceScheduler;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.ops.ApplicationCreateOperation;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.TimeoutExceededException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

/**
 *
 */
@Singleton
@Slf4j
public class ApplicationLifecycleManager {
    //    private final ExecutorService executorService;
    private final InstanceScheduler scheduler;
    private final ApplicationStateDB applicationStateDB;
    private final ControllerCommunicator communicator;
    private final ConsumingSyncSignal<ApplicationStateChangeInfo> stateChanged = new ConsumingSyncSignal<>();

    @Inject
    public ApplicationLifecycleManager(
//            ExecutorService executorService,
            InstanceScheduler scheduler,
            ApplicationStateDB applicationStateDB,
            ControllerCommunicator communicator) {
//        this.executorService = executorService;
        this.scheduler = scheduler;
        this.applicationStateDB = applicationStateDB;
        this.communicator = communicator;
    }

    public ConsumingSyncSignal<ApplicationStateChangeInfo> onStateChanged() {
        return stateChanged;
    }

    public boolean start(final ApplicationCreateOperation createOperation) {
        val applicationSpec = createOperation.getSpec();
        val clusterOpSpec = createOperation.getOpSpec();
        val parallelism = clusterOpSpec.getParallelism();
        val executorService = Executors.newFixedThreadPool(parallelism);
        val cs = new ExecutorCompletionService<Boolean>(executorService);
        IntStream.range(0, applicationSpec.getInstances())
                .forEach(i -> cs.submit(() -> startSingleInstance(applicationSpec, clusterOpSpec)));
        val appId = appId(applicationSpec);
        return IntStream.range(0, applicationSpec.getInstances())
                .mapToObj(i -> {
                    try {
                        return cs.take().get();
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    catch (ExecutionException e) {
                        log.error("Error starting an instance for: " + appId, e);
                    }
                    return false;
                })
                .allMatch(r -> r);
    }

    private boolean startSingleInstance(ApplicationSpec applicationSpec, ClusterOpSpec clusterOpSpec) {
        val retryPolicy = new RetryPolicy<Boolean>()
                .withDelay(Duration.ofSeconds(30))
                .withMaxDuration(Duration.ofMinutes(3))
                .handle(Exception.class)
                .handleResultIf(r -> !r);
        val appId = appId(applicationSpec);
        try {
            return Failsafe.with(retryPolicy)
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
        val node = scheduler.schedule(applicationSpec)
                .orElse(null);
        if (null == node) {
            log.warn("No node found in the cluster that can provide required resources" +
                             " and satisfy the placement policy needed for {}.", appId(applicationSpec));
            return false;
        }
        val appId = appId(applicationSpec);
        val instanceId = UUID.randomUUID().toString();
        val startMessage = new StartInstanceMessage(MessageHeader.controllerRequest(),
                                                    new ExecutorAddress(node.getExecutorId(),
                                                                        node.getHostname(),
                                                                        node.getPort()),
                                                    new InstanceSpec(appId,
                                                                     instanceId,
                                                                     applicationSpec.getExecutable(),
                                                                     List.of(node.getCpu(),
                                                                             node.getMemory()),
                                                                     applicationSpec.getExposedPorts(),
                                                                     applicationSpec.getVolumes(),
                                                                     applicationSpec.getHealthcheck(),
                                                                     applicationSpec.getReadiness(),
                                                                     applicationSpec.getEnv()));
        communicator.send(startMessage);
        log.debug("Sent message to start instance: {}/{}. Message: {}", appId, instanceId, startMessage);
        return ensureInstanceState(clusterOpSpec, appId, instanceId, InstanceState.HEALTHY);
        //TODO::IDENTIFY THE STATES AND DO NOT GIVE UP QUICKLY IF IT IS IN STARTING STATE ETC
    }

    private boolean ensureInstanceState(ClusterOpSpec clusterOpSpec, String appId, String instanceId, InstanceState required) {
        val retryPolicy = new RetryPolicy<Boolean>()
                .withDelay(Duration.ofSeconds(3))
                .withMaxAttempts(50)
                .withMaxDuration(Duration.ofMillis(clusterOpSpec.getTimeout().toMilliseconds()))
                .handle(Exception.class)
                .handleResultIf(r -> !r);

        try {
            val status = Failsafe.with(retryPolicy)
                    .onComplete(e -> {
                        val failure = e.getFailure();
                        if (null != failure) {
                            log.error("Error starting instance: {}", failure.getMessage());
                        }
                    })
                    .get(() -> ensureInstanceState(currentInstanceInfo(appId, instanceId), required));
            if (status) {
                return true;
            }
            else {
                val curr = currentInstanceInfo(appId, instanceId);
                if (null == curr) {
                    log.error("No instance info found at all for: {}/{}", appId, instanceId);
                }
                else {
                    log.error("Looks like {}/{} is stuck in state: {}. Detailed instance data: {}}",
                              appId, instanceId, curr.getState(), curr);
                }
            }
        }
        catch (Exception e) {
            log.error("Error starting instance: " + appId + "/" + instanceId, e);
        }
        return false;
    }

    private InstanceInfo currentInstanceInfo(String appId, String instanceId) {
        return applicationStateDB.instance(appId, instanceId).orElse(null);
    }

    private String appId(ApplicationSpec applicationSpec) {
        return applicationSpec.getName();
    }

    private boolean ensureInstanceState(final InstanceInfo instanceInfo, final InstanceState instanceState) {
        if (null == instanceInfo) {
            return false;
        }
        log.debug("Intsance state for {}/{}: {}",
                  instanceInfo.getAppId(),
                  instanceInfo.getInstanceId(),
                  instanceInfo.getState());
        return instanceInfo.getState() == instanceState;
    }
}
