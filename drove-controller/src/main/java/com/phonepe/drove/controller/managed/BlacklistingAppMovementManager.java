package com.phonepe.drove.controller.managed;

import com.google.common.annotations.VisibleForTesting;
import com.phonepe.drove.common.model.utils.Pair;
import com.phonepe.drove.controller.engine.ApplicationEngine;
import com.phonepe.drove.controller.engine.ValidationStatus;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.ops.ApplicationReplaceInstancesOperation;
import io.dropwizard.lifecycle.Managed;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;
import org.jetbrains.annotations.NotNull;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Moves instances from blacklisted hosts in a sequential manner to ensure app commands do not fail due to being busy
 */
@Order(70)
@Slf4j
@Singleton
public class BlacklistingAppMovementManager implements Managed {
    static final RetryPolicy<ValidationStatus> DEFAULT_COMMAND_POLICY = new RetryPolicy<ValidationStatus>()
            .onFailedAttempt(event -> log.warn("Command submission attempt: {}", event.getAttemptCount()))
            .handleResult(ValidationStatus.FAILURE)
            .withMaxAttempts(5)
            .withDelay(10, 30, ChronoUnit.SECONDS);

    static final RetryPolicy<Boolean> DEFAULT_COMPLETION_POLICY = new RetryPolicy<Boolean>()
            .onFailedAttempt(event -> log.warn("Executor check attempt: {}", event.getAttemptCount()))
            .handleResult(false)
            .withMaxDuration(Duration.ofMinutes(15))
            .withDelay(10, 30, ChronoUnit.SECONDS);
    private final BlockingQueue<Set<String>> queue = new LinkedBlockingQueue<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final ApplicationEngine applicationEngine;
    private final ClusterResourcesDB clusterResourcesDB;

    private final RetryPolicy<ValidationStatus> opSubmissionPolicy;

    private final RetryPolicy<Boolean> noInstanceEnsurerPolicy;
    private final ClusterOpSpec defaultClusterOpSpec;
    private final Future<?> future;

    @Inject
    public BlacklistingAppMovementManager(
            LeadershipEnsurer leadershipEnsurer,
            ApplicationEngine applicationEngine,
            ClusterResourcesDB clusterResourcesDB,
            ClusterOpSpec defaultClusterOpSpec) {
        this(leadershipEnsurer,
             applicationEngine,
             clusterResourcesDB,
             DEFAULT_COMMAND_POLICY,
             DEFAULT_COMPLETION_POLICY,
             defaultClusterOpSpec);
    }

    @VisibleForTesting
    BlacklistingAppMovementManager(
            LeadershipEnsurer leadershipEnsurer,
            ApplicationEngine applicationEngine,
            ClusterResourcesDB clusterResourcesDB,
            RetryPolicy<ValidationStatus> opSubmissionPolicy,
            RetryPolicy<Boolean> noInstanceEnsurerPolicy,
            ClusterOpSpec defaultClusterOpSpec) {
        this.applicationEngine = applicationEngine;
        this.clusterResourcesDB = clusterResourcesDB;
        this.opSubmissionPolicy = opSubmissionPolicy;
        this.noInstanceEnsurerPolicy = noInstanceEnsurerPolicy;
        this.defaultClusterOpSpec = defaultClusterOpSpec;
        this.future = executorService.submit(this::processQueuedElement);
        leadershipEnsurer.onLeadershipStateChanged().connect(this::handleLeadershipChanged);
    }

    private void handleLeadershipChanged(boolean isLeader) {
        if (!isLeader) {
            log.debug("Doing nothing as I'm not the leader");
        }
        log.info("Node became leader, checking if any instances need to be moved from blacklisted nodes");
        val eligibleExecutors = clusterResourcesDB.currentSnapshot(true)
                .stream()
                .filter(info -> info.getNodeData()
                        .getInstances()
                        .stream()
                        .anyMatch(instanceInfo -> instanceInfo.getState().equals(InstanceState.HEALTHY)))
                .map(info -> {
                    val executorId = info.getExecutorId();
                    log.info("Looks like executor {} was blacklisted but apps did not get moved. Moving them now",
                             executorId);
                    return executorId;
                })
                .collect(Collectors.toUnmodifiableSet());
        if (!eligibleExecutors.isEmpty()) {
            val status = queue.offer(eligibleExecutors);
            log.info("Executor {} blacklisting movement queuing status: {}", eligibleExecutors, status);
        }
    }

    @SneakyThrows
    public boolean moveApps(final Set<String> executorId) {
        return queue.offer(executorId);
    }


    private void processQueuedElement() {
        try {
            while (true) {
                val executorIds = queue.take();
                log.info("Moving app instances from execuitors: {}", executorIds);
                moveAppsFromExecutors(executorIds);
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Blacklist manager interrupted");
        }
    }

    private void moveAppsFromExecutors(final Set<String> executorIds) {
        val healthyInstances = healthyInstances(executorIds);
        val anyFailed = new AtomicBoolean();
        healthyInstances.forEach((appId, instances) -> {
            try {
                val finalStatus = Failsafe.with(opSubmissionPolicy)
                        .get(() -> {
                            val res = applicationEngine.handleOperation(
                                    new ApplicationReplaceInstancesOperation(appId,
                                                                             instances,
                                                                             defaultClusterOpSpec));
                            log.info("Instances to be replaced for {}: {}. command acceptance status: {}",
                                     appId,
                                     instances,
                                     res);
                            return res.getStatus();
                        });

                log.info("Final status for op acceptance for {}/{} is {}", appId, instances, finalStatus);
                anyFailed.compareAndExchange(false, finalStatus == ValidationStatus.FAILURE);
            }
            catch (FailsafeException e) {
                log.info("Failed to send command for app movement for: " + appId, e);
                anyFailed.compareAndExchange(false, true);
            }
        });

        if (anyFailed.get()) {
            log.error("Could not shut down instances on {}. Check log for details.", executorIds);
        }
        else {
            try {
                val allClear = Failsafe.with(noInstanceEnsurerPolicy).get(() -> healthyInstances(executorIds).isEmpty());
                if(allClear) {
                    log.info("All app instances moved from executors {}", executorIds);
                }
                else {
                    log.info("All app instances have not been moved from executors {}", executorIds);
                }
            }
            catch (FailsafeException e) {
                log.error("Failed to ensure all instances have been moved from executors " + executorIds, e);
            }
        }
    }

    @NotNull
    private Map<String, Set<String>> healthyInstances(final Set<String> executorIds) {
        //healthy instances might temporarily reside on blacklisted nodes while app movement underway
        return clusterResourcesDB.currentSnapshot(false)
                .stream()
                .filter(snapshot -> executorIds.contains(snapshot.getExecutorId()))
                .flatMap(snapshot -> snapshot.getNodeData().getInstances()
                        .stream()
                        .filter(instanceInfo -> instanceInfo.getState().equals(InstanceState.HEALTHY))
                        .map(instanceInfo -> new Pair<>(instanceInfo.getAppId(), instanceInfo.getInstanceId())))
                .collect(Collectors.groupingBy(Pair::getFirst,
                                               Collectors.mapping(Pair::getSecond, Collectors.toUnmodifiableSet())));
    }

    @Override
    public void start() {
        log.info("Blacklisting manager started");
    }

    @Override
    public void stop() throws Exception {
        future.cancel(true);
        executorService.shutdown();
        log.info("Blacklisting manager stopped");
    }
}
