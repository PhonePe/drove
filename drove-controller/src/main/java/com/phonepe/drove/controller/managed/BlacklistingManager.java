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

package com.phonepe.drove.controller.managed;

import static com.phonepe.drove.common.CommonUtils.waitForAction;
import static com.phonepe.drove.controller.utils.EventUtils.executorMetadata;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.google.common.annotations.VisibleForTesting;
import com.phonepe.drove.common.discovery.Constants;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.executor.BlacklistExecutorMessage;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.model.executor.UnBlacklistExecutorMessage;
import com.phonepe.drove.common.model.utils.Pair;
import com.phonepe.drove.controller.engine.ApplicationLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.ValidationStatus;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.events.events.DroveExecutorBlacklistedEvent;
import com.phonepe.drove.models.events.events.DroveExecutorUnblacklistedEvent;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.ops.ApplicationReplaceInstancesOperation;

import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeException;
import dev.failsafe.RetryPolicy;
import io.dropwizard.lifecycle.Managed;
import lombok.SneakyThrows;
import lombok.val;
import lombok.extern.slf4j.Slf4j;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

/**
 * Moves instances from blacklisted hosts in a sequential manner to ensure app commands do not fail due to being busy
 */
@Order(70)
@Slf4j
@Singleton
public class BlacklistingManager implements Managed {
    static final RetryPolicy<ValidationStatus> DEFAULT_COMMAND_POLICY = RetryPolicy.<ValidationStatus>builder()
            .onFailedAttempt(event -> log.warn("Command submission attempt: {}", event.getAttemptCount()))
            .handleResult(ValidationStatus.FAILURE)
            .withMaxAttempts(-1)
            .withMaxDuration(Duration.ofMinutes(5))
            .withDelay(10, 30, ChronoUnit.SECONDS)  //Will try for 5 minutes to get the command accepted
            .build();

    static final RetryPolicy<Boolean> DEFAULT_COMPLETION_POLICY = RetryPolicy.<Boolean>builder()
            .onFailedAttempt(event -> log.warn("Executor check attempt: {}", event.getAttemptCount()))
            .handleResult(false)
            .withMaxAttempts(-1)
            .withMaxDuration(Duration.ofMinutes(15))
            .withDelay(10, 30, ChronoUnit.SECONDS) //Will wait 15 minutes for the app to move
            .build();

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final Set<String> processing = new HashSet<>();

    //The following needs to be single threaded. Parallel blacklisting will lead to command
    // rejections due to app being busy for existing blacklisting
    private final ExecutorService queuePollingExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService appMovementExecutor;

    private final ApplicationLifecycleManagementEngine applicationEngine;
    private final ClusterResourcesDB clusterResourcesDB;
    private final ControllerCommunicator communicator;
    private final DroveEventBus eventBus;

    private final ClusterOpSpec defaultClusterOpSpec;
    private final long initialWaitTime;
    private final Future<?> future;

    private final Timer checkAfterExecutorRefreshTimer = new Timer();

    @Inject
    public BlacklistingManager(
            LeadershipEnsurer leadershipEnsurer,
            ApplicationLifecycleManagementEngine applicationEngine,
            ClusterResourcesDB clusterResourcesDB,
            ControllerCommunicator communicator,
            DroveEventBus eventBus,
            ClusterOpSpec defaultClusterOpSpec,
            @Named("BlacklistingAppMover") ExecutorService appMovementExecutor) {
        this(leadershipEnsurer,
             applicationEngine,
             clusterResourcesDB,
             communicator,
             eventBus,
             defaultClusterOpSpec,
             appMovementExecutor,
             Constants.EXECUTOR_REFRESH_INTERVAL.toMillis() * 2 + 5); //Wait till whole cluster refreshes at least once
    }

    @SuppressWarnings("java:S107")
    @VisibleForTesting
    BlacklistingManager(
            LeadershipEnsurer leadershipEnsurer,
            ApplicationLifecycleManagementEngine applicationEngine,
            ClusterResourcesDB clusterResourcesDB,
            ControllerCommunicator communicator,
            DroveEventBus eventBus,
            ClusterOpSpec defaultClusterOpSpec,
            ExecutorService appMovementExecutor,
            long initialWaitTime) {
        this.appMovementExecutor = appMovementExecutor;
        this.applicationEngine = applicationEngine;
        this.clusterResourcesDB = clusterResourcesDB;
        this.communicator = communicator;
        this.eventBus = eventBus;
        this.defaultClusterOpSpec = defaultClusterOpSpec;
        this.initialWaitTime = initialWaitTime;
        this.future = queuePollingExecutor.submit(this::processQueuedElement);
        leadershipEnsurer.onLeadershipStateChanged().connect(this::handleLeadershipChanged);
    }

    public Set<String> blacklistExecutors(Set<String> executorIds) {
        boolean isFree = lock.tryLock();
        if (!isFree) {
            log.warn("Blacklisting underway already. Will not accept new requests");
            return Set.of();
        }
        try {
            return blacklistExecutorsInternal(executorIds);
        }
        finally{
            lock.unlock();
        }
    }

    public Set<String> unblacklistExecutors(final Set<String> executorIds) {
        val successfullyMessageSent = executorIds.stream()
                .filter(executorId -> {
                    val executor = clusterResourcesDB.currentSnapshot(executorId).orElse(null);
                    if (null != executor) {
                        val msgResponse = communicator.send(
                                new UnBlacklistExecutorMessage(MessageHeader.controllerRequest(),
                                                               new ExecutorAddress(executor.getExecutorId(),
                                                                                   executor.getNodeData().getHostname(),
                                                                                   executor.getNodeData().getPort(),
                                                                                   executor.getNodeData()
                                                                                           .getTransportType())));
                        if (msgResponse.getStatus().equals(MessageDeliveryStatus.ACCEPTED)) {
                            log.debug("Executor {} marked unblacklisted.", executorId);
                            eventBus.publish(new DroveExecutorUnblacklistedEvent(executorMetadata(executor.getNodeData())));
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toUnmodifiableSet());
        val waitpolicy = blacklistingRelatedWaitPolicy(successfullyMessageSent);
        try {
            Failsafe.with(List.of(waitpolicy))
                .get(() -> findCurrentOverlappingUnblacklisted(successfullyMessageSent));
        }
        catch (FailsafeException e) {
            log.error("Blacklisting did not reflect in cluster resources DB for executors: " + successfullyMessageSent, e);
        }
        val finallyUnblacklisted = findCurrentOverlappingUnblacklisted(successfullyMessageSent);
        if(finallyUnblacklisted.isEmpty()) {
            log.error("Unlacklisting did not reflect in cluster resources DB for executors: {}", successfullyMessageSent);
            return Set.of();
        }
        return finallyUnblacklisted;
    }

    @SneakyThrows
    public boolean moveApps(final Set<String> executorIds) {
        lock.lock();
        try {
            return moveAppsInternal(executorIds);
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public void start() {
        log.info("Blacklisting manager started");
    }

    @Override
    public void stop() throws Exception {
        future.cancel(true);
        queuePollingExecutor.shutdown();
        log.info("Blacklisting manager stopped");
    }

    private static RetryPolicy<Set<String>> blacklistingRelatedWaitPolicy(final Set<String> targetExecutorIds) {
        return RetryPolicy.<Set<String>>builder()
            .onFailedAttempt(event -> log.warn("Waiting for blacklising to reflect in cluster resources DB. Attempt: {}", event.getAttemptCount()))
            .handleResultIf(result -> !result.containsAll(targetExecutorIds))
            .withMaxAttempts(-1)
            .withMaxDuration(Duration.ofMinutes(1))
            .withDelay(1, 5, ChronoUnit.SECONDS)
            .build();
    }

    private static RetryPolicy<Boolean> allAppInstancesMovedWaitPolicy(long timeout) {
        return RetryPolicy.<Boolean>builder()
            .onFailedAttempt(event -> log.warn("Executor check attempt: {}", event.getAttemptCount()))
            .handleResult(false)
            .withMaxAttempts(-1)
            .withMaxDuration(Duration.ofMillis(timeout))
            .withDelay(10, 30, ChronoUnit.SECONDS) //Will wait 15 minutes for the app to move
            .build();
    }

    private boolean moveAppsInternal(final Set<String> executorIds) {
        val status = processing.addAll(executorIds);
            if (status) {
                condition.signalAll();
            }
            else {
                log.info("Did not schedule executors for app movement. Looks like app movement is already underway.");
            }
            return status;
    }

    private Set<String> blacklistExecutorsInternal(Set<String> executorIds) {
        val successfullyBlacklistCalled = executorIds.stream()
            .filter(executorId -> {
                if (clusterResourcesDB.isBlacklisted(executorId)) {
                    log.warn("Executor {} is set to blacklisted already. We are going to skip this.", executorId);
                    return false;
                }
                val executor = clusterResourcesDB.currentSnapshot(executorId).orElse(null);
                if (null != executor) {
                    val msgResponse = communicator.send(
                            new BlacklistExecutorMessage(MessageHeader.controllerRequest(),
                                new ExecutorAddress(executor.getExecutorId(),
                                    executor.getNodeData().getHostname(),
                                    executor.getNodeData().getPort(),
                                    executor.getNodeData()
                                    .getTransportType())));
                    if (msgResponse.getStatus().equals(MessageDeliveryStatus.ACCEPTED)) {
                        log.info("Executor {} has been marked as blacklisted. Moving running instances",
                                executorId);
                        eventBus.publish(new DroveExecutorBlacklistedEvent(executorMetadata(executor.getNodeData())));
                        return true;
                    }
                    else {
                        log.error("Error sending blacklist message to executor {}. Status: {}",
                                executorId,
                                msgResponse.getStatus());
                    }
                }
                return false;
            })
        .collect(Collectors.toUnmodifiableSet());
        if (successfullyBlacklistCalled.isEmpty()) {
            log.warn("No executor has been blacklisted. No apps to move.");
            return Set.of();
        }
        val waitpolicy = blacklistingRelatedWaitPolicy(successfullyBlacklistCalled);
        try {
            Failsafe.with(List.of(waitpolicy))
                .get(clusterResourcesDB::blacklistedNodes);
        }
        catch (FailsafeException e) {
            log.error("Blacklisting did not reflect in cluster resources DB for executors: " + successfullyBlacklistCalled, e);
        }
        val currentBlackListed = clusterResourcesDB.blacklistedNodes();
        val finallyBlacklisted = successfullyBlacklistCalled.stream()
            .filter(currentBlackListed::contains)
            .collect(Collectors.toUnmodifiableSet());
        if(finallyBlacklisted.isEmpty()) {
            log.error("Blacklisting did not reflect in cluster resources DB for executors: {}", successfullyBlacklistCalled);
            return Set.of();
        }
        val status = moveApps(finallyBlacklisted);
        log.info("App movement manager acceptance status for executors {} is {}", successfullyBlacklistCalled, status);
        return finallyBlacklisted;
    }

    private void handleLeadershipChanged(boolean isLeader) {
        if (!isLeader) {
            log.debug("Doing nothing as I'm not the leader");
            return;
        }
        //Timer task is needed below, because otherwise during complete cluster startup, the cluster status might not
        // have formed completely to take an informed decision
        val task = new TimerTask() {
            @Override
            public void run() {
                log.info("Node became leader, checking if any instances need to be moved from blacklisted nodes");
                val eligibleExecutors = clusterResourcesDB.currentSnapshot(false)
                        .stream()
                        .filter(info -> clusterResourcesDB.isBlacklisted(info.getExecutorId()))
                        .filter(info -> info.getNodeData()
                                .getInstances()
                                .stream()
                                .anyMatch(instanceInfo -> instanceInfo.getState().equals(InstanceState.HEALTHY)))
                        .map(info -> {
                            val executorId = info.getExecutorId();
                            log.info(
                                    "Looks like executor {} was blacklisted but apps did not get moved. Moving them " +
                                            "now",
                                    executorId);
                            return executorId;
                        })
                        .collect(Collectors.toUnmodifiableSet());
                if (!eligibleExecutors.isEmpty()) {
                    val status = moveApps(eligibleExecutors);
                    log.info("Executor {} blacklisting movement queuing status: {}", eligibleExecutors, status);
                }
                else {
                    log.info("No blacklisted node on the cluster");
                }
            }
        };
        //Give some time for all nodes to have sent updates
        log.info("Will check for blacklisting status after {} ms", initialWaitTime);
        checkAfterExecutorRefreshTimer.schedule(task, initialWaitTime);
    }

    private Set<String> findCurrentOverlappingUnblacklisted(final Set<String> successfullyMessageSent) {
        return clusterResourcesDB.currentSnapshot(true)
                .stream()
                .filter(info -> successfullyMessageSent.contains(info.getExecutorId()))
                .map(ExecutorHostInfo::getExecutorId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void processQueuedElement() {
        while (true) {
            lock.lock();
            try {
                while (true) {
                    condition.await();
                    if (processing.isEmpty()) {
                        log.debug("Spurious wakeup. No new executor to be scheduled for app movement");
                    }
                    else {
                        break;
                    }
                }
                log.info("Moving app instances from executors: {}", processing);
                moveAppsFromExecutors(processing);
                processing.clear();
                log.info("Apps moved");
            }
            catch (InterruptedException e) {
                log.info("Blacklist manager interrupted");
                Thread.currentThread().interrupt();
                break;
            }
            catch (Exception e) {
                log.error("Error in blacklist polling thread: " + e.getMessage(), e);
            }
            finally {
                lock.unlock();
            }
        }
    }

    private static RetryPolicy<ValidationStatus> appMovementWaitPolicy(long timeout) {
        return RetryPolicy.<ValidationStatus>builder()
            .onFailedAttempt(event -> log.warn("Command submission attempt: {}", event.getAttemptCount()))
            .handleResult(ValidationStatus.FAILURE)
            .withMaxAttempts(-1)
            .withMaxDuration(Duration.ofMillis(timeout + 30_000)) // We add a buffer
            .withDelay(10, 30, ChronoUnit.SECONDS)  //Will try for 5 minutes to get the command accepted
            .build();
    }

    private void moveAppsFromExecutors(final Set<String> executorIds) {
        val healthyInstances = healthyInstances(executorIds);
        if (healthyInstances.isEmpty()) {
            log.info("Nothing to do as no app instances need to be moved from executors: {}", executorIds);
            return;
        }
        val completionService = new ExecutorCompletionService<Pair<String, Boolean>>(appMovementExecutor);
        //Raise replacement request for multiple apps in parallel
        //Wait for all apps to submit successfully
        //Then poll for executors to have become empty
        val defaultTimeout = defaultClusterOpSpec.getTimeout().toMilliseconds();
        val parallelism = defaultClusterOpSpec.getParallelism();
        val futures = new ArrayList<Future<Pair<String, Boolean>>>();
        val maxTimeToWait = new AtomicLong(0);
        for(val entry : healthyInstances.entrySet()) {
            val appId = entry.getKey();
            val instances = entry.getValue();
            val timeoutMultiplier = Math.max(1, instances.size() / parallelism);
            val computedTimeout = applicationEngine.getStateDB()
                     .application(appId)
                     .map(ApplicationInfo::getSpec)
                     .map(spec -> timeoutMultiplier * (ControllerUtils.maxStartTimeout(spec) + ControllerUtils .maxStopTimeout(spec)))
                     .orElse(defaultTimeout);
            val opTimeout = Math.max(defaultTimeout, computedTimeout);
            maxTimeToWait.updateAndGet(current -> Math.max(current, opTimeout));
            log.debug("Calculated timeout for app {} with {} instances is {} ms. Default: {} ms. Computed: {} ms. Parallelism: {}. Timeout multipler: {}",
                    appId, instances, opTimeout, defaultTimeout, computedTimeout, parallelism, timeoutMultiplier);
            val waitTime = opTimeout + 30_000; //Adding some buffer to the wait time
            val future = completionService.submit(() -> issueReplaceCommand(waitTime,
                                                                            appId,
                                                                            instances,
                                                                            opTimeout));
            futures.add(future);
        }
        val failedApps = futures.stream()
                .map(f -> {
                    try {
                        return completionService.take().get();
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    catch (ExecutionException e) {
                        log.error("Error getting app instance replacement status. Error: " + e.getMessage(), e);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .filter(pair -> !pair.getSecond())
                .map(Pair::getFirst)
                .collect(Collectors.toUnmodifiableSet());
        if (!failedApps.isEmpty()) {
            log.error("Could not shut down instances on {}. Failed apps: {}. Check log for details.",
                      executorIds, failedApps);
        }
        else {
            val totalWait = maxTimeToWait.get();
            log.info("Commands accepted for all relevant app instances to be moved. Will wait for {}ms to ensure completion.", totalWait);
            try {
                val allClear = waitForAction(allAppInstancesMovedWaitPolicy(totalWait),
                                             () -> healthyInstances(executorIds).isEmpty());
                if (allClear) {
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

    private Pair<String, Boolean> issueReplaceCommand(
                                            final long waitTime,
                                            final String appId,
                                            final Set<String> instances,
                                            final long opTimeout) {
        try {
            val finalStatus = Failsafe.with(List.of(appMovementWaitPolicy(waitTime)))
                    .get(() -> {
                        val res = applicationEngine.handleOperation(
                                                                    new ApplicationReplaceInstancesOperation(appId,
                                                                                                             instances,
                                                                                                             false,
                                                                                                             defaultClusterOpSpec
                                                                                                                     .withTimeout(io.dropwizard.util.Duration
                                                                                                                             .milliseconds(opTimeout))));
                        log.info("Instances to be replaced for {}: {}. command acceptance status: {}. Timeout: {} ms",
                                appId, instances, res, opTimeout);
                        return res.getStatus();
                    });
            return new Pair<>(appId, finalStatus == ValidationStatus.SUCCESS);
        }
        catch (FailsafeException e) {
            log.info("Failed to send command for app movement for: " + appId, e);
        }
        return new Pair<>(appId, false);
    }

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

}
