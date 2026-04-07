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
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.phonepe.drove.common.discovery.Constants;
import com.phonepe.drove.common.model.ExecutorMessageType;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.executor.BlacklistExecutorFinalizeMessage;
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
import com.phonepe.drove.models.api.BlacklistOperationResponse;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.events.events.DroveExecutorBlacklistRequestedEvent;
import com.phonepe.drove.models.events.events.DroveExecutorBlacklistedEvent;
import com.phonepe.drove.models.events.events.DroveExecutorUnblacklistedEvent;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.ops.ApplicationReplaceInstancesOperation;

import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeException;
import dev.failsafe.RetryPolicy;
import io.dropwizard.lifecycle.Managed;
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

    //Response for moveApps() API.
    public record MoveOutResponse(boolean status, long maxWaitTimeMs){}

    //Data required to move out instances of an app from blacklisted executors
    private record ReplacementDetails(String appId, Set<String> instances, long waitTime, long operationTimeout) {}

    //The executors and the relevant app instance details that are being processed for movement currently
    private record MovementDetails(Set<String> executorIds, List<ReplacementDetails> replacementDetails) {}

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final AtomicReference<MovementDetails> processing = new AtomicReference<>(null);

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

    public BlacklistOperationResponse blacklistExecutors(Set<String> executorIds) {
        val locked = lock.tryLock();
        if (!locked) {
            return reject(executorIds);
        }
        try {
            return blacklistExecutorsInternal(executorIds);
        }
        finally{
            lock.unlock();
        }
    }

    public BlacklistOperationResponse unblacklistExecutors(Set<String> executorIds) {
        val locked = lock.tryLock();
        if (!locked) {
            return reject(executorIds);
        }
        try {
            val unblacklisted = unblacklistExecutorsInternal(executorIds);
            val failed = Sets.difference(executorIds, unblacklisted);
            return BlacklistOperationResponse.builder()
                .successful(unblacklisted)
                .failed(Set.copyOf(failed))
                .message(failed.isEmpty()
                        ? "Unblacklisting successful for all executors"
                        : "Unblacklisting successful for some executors. Check logs for details.")
                .build();
        }
        finally{
            lock.unlock();
        }
    }

    public MoveOutResponse moveApps(Set<String> executorIds) {
        lock.lock();
        try {
            val defaultTimeout = defaultClusterOpSpec.getTimeout().toMilliseconds();
            val replacementDetails = calculateReplacementDetails(healthyInstances(executorIds), defaultTimeout);
            val maxTimeToWait = replacementDetails.stream()
                .mapToLong(ReplacementDetails::operationTimeout)
                .max()
                .orElseGet(() -> replacementDetails.isEmpty() ? 0L : defaultTimeout);
            processing.set(new MovementDetails(executorIds, replacementDetails));
            condition.signalAll();
            return new MoveOutResponse(true, maxTimeToWait);
        }
        catch(Exception e) {
            log.error("Could not schedule app movement from executors: %s".formatted(executorIds), e);
            return new MoveOutResponse(false, 0L);
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

    private static BlacklistOperationResponse reject(Set<String> executorIds) {
        log.warn("Blacklisting underway already. Will not accept new requests");
        return failed(executorIds, "A blacklisting related operation is underway already. Will not accept new requests");
    }


    private static BlacklistOperationResponse failed(final Set<String> executorIds, String message) {
        return BlacklistOperationResponse.builder()
                .successful(Set.of())
                .failed(executorIds)
                .approxCompletionTimeMs(0L)
                .message(message)
                .build();
    }

    private BlacklistOperationResponse blacklistExecutorsInternal(Set<String> executorIds) {
        val successfullyBlacklistCalled = executorIds.stream()
            .filter(executorId -> {
                val executor = clusterResourcesDB.currentSnapshot(executorId).orElse(null);
                if (null != executor) {
                    if (executor.getNodeData().getExecutorState().equals(ExecutorState.BLACKLISTED)) {
                        log.warn("Executor {} is blacklisted already. We are going to skip this.", executorId);
                        return false;
                    }
                    return sendExecutorMessage(executor, ExecutorMessageType.BLACKLIST_REQUESTED);
               }
                return false;
            })
        .collect(Collectors.toUnmodifiableSet());
        if (successfullyBlacklistCalled.isEmpty()) {
            log.error("Blacklisting messages could not be sent to any of the executors: {}", executorIds);
            return failed(executorIds, "Blacklisting messages could not be sent to any of the executors.");
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
        val finallyBlacklisted = Sets.intersection(executorIds, currentBlackListed);
        if(finallyBlacklisted.isEmpty()) {
            log.error("Blacklisting did not reflect in cluster resources DB for executors: {}", successfullyBlacklistCalled);
            return failed(executorIds, "Blacklisting did not reflect in cluster resources DB for any of the executors.");
        }
        val response = moveApps(finallyBlacklisted);
        log.info("App movement manager acceptance status for executors {} is {}", successfullyBlacklistCalled, response.status());
        val message = response.status()
            ? "Blacklisting successful and app movement scheduled"
            : "Blacklisting successful but app movement scheduling failed";
        return BlacklistOperationResponse.builder()
                .successful(Set.copyOf(finallyBlacklisted))
                .failed(Set.copyOf(Sets.difference(executorIds, finallyBlacklisted)))
                .approxCompletionTimeMs(response.maxWaitTimeMs())
                .message(message)
                .build();
    }

    private Set<String> unblacklistExecutorsInternal(final Set<String> executorIds) {
        val successfullyMessageSent = executorIds.stream()
                .filter(executorId -> {
                    val executor = clusterResourcesDB.currentSnapshot(executorId).orElse(null);
                    if (null != executor) {
                        return sendExecutorMessage(executor, ExecutorMessageType.UNBLACKLIST);
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

    private boolean sendExecutorMessage(ExecutorHostInfo executor, ExecutorMessageType messageType) {
        val address = new ExecutorAddress(executor.getExecutorId(),
                                          executor.getNodeData().getHostname(),
                                          executor.getNodeData().getPort(),
                                          executor.getNodeData()
                                                  .getTransportType());
        val message = switch (messageType) {
            case BLACKLIST_REQUESTED -> new BlacklistExecutorMessage(MessageHeader.controllerRequest(), address);
            case BLACKLIST -> new BlacklistExecutorFinalizeMessage(MessageHeader.controllerRequest(), address);
            case UNBLACKLIST -> new UnBlacklistExecutorMessage(MessageHeader.controllerRequest(), address);
            default -> throw new IllegalArgumentException("Unsupported message type for blacklisting flow: "
                    + messageType);
        };
        val msgResponse = communicator.send(message);
        if (msgResponse.getStatus().equals(MessageDeliveryStatus.ACCEPTED)) {
            log.info("Executor {} has accepted message of type: {}", executor.getExecutorId(), messageType);
            val event = switch (messageType) {
                case BLACKLIST_REQUESTED -> new DroveExecutorBlacklistRequestedEvent(executorMetadata(executor.getNodeData()));
                case BLACKLIST -> new DroveExecutorBlacklistedEvent(executorMetadata(executor.getNodeData()));
                case UNBLACKLIST -> new DroveExecutorUnblacklistedEvent(executorMetadata(executor.getNodeData()));
                default -> throw new IllegalArgumentException("Unsupported message type for blacklisting flow: "
                        + messageType);
            };
            eventBus.publish(event);
            return true;
        }
        log.error("Error sending {} message to executor {}. Status: {}",
                  messageType,
                  executor.getExecutorId(),
                  msgResponse.getStatus());
        return false;
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
                        .filter(info -> info.getNodeData().getExecutorState().equals(ExecutorState.BLACKLIST_REQUESTED))
                        .map(ExecutorHostInfo::getExecutorId)
                        .collect(Collectors.toUnmodifiableSet());
                if (!eligibleExecutors.isEmpty()) {
                    log.info("Found executors in blacklisting requested state. Scheduling app movement for them: {}",
                            eligibleExecutors);
                    val status = moveApps(eligibleExecutors).status();
                    log.info("Executor {} blacklisting app movement queuing status: {}", eligibleExecutors, status);
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
                    if (processing.get() == null) {
                        log.debug("Spurious wakeup. No new executor to be scheduled for app movement");
                    }
                    else {
                        break;
                    }
                }
                log.info("Moving app instances from executors: {}", processing);
                val movementDetails = processing.get();
                handleMovement(movementDetails);
                processing.set(null);
                log.info("Apps moved. Blacklisting can proceed for executors: {}", movementDetails.executorIds());
                movementDetails.executorIds()
                        .forEach(executorId -> {
                            val executor = clusterResourcesDB.currentSnapshot(executorId).orElse(null);
                            if (null != executor) {
                                sendExecutorMessage(executor, ExecutorMessageType.BLACKLIST);
                            }
                        });
                log.info("Blacklisting complete for executors: {}", movementDetails.executorIds());
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

    private void handleMovement(final MovementDetails movementDetails) {
        val hasHealthyInstances = movementDetails.replacementDetails()
            .stream()
            .anyMatch(replacementData -> !replacementData.instances().isEmpty());
        val executorIds = movementDetails.executorIds();
        if (!hasHealthyInstances) {
            log.info("Nothing to do as no app instances need to be moved from executors: {}", executorIds);
        }
        else {
            log.info("Moving app instances from executors: {}", executorIds);
            try {
                moveAppsFromExecutors(executorIds, movementDetails.replacementDetails());
            }
            catch (Exception e) {
                log.error("Error moving apps from executors " + executorIds, e);
            }
        }
        log.info("Finished processing app movement for executors: {}", executorIds);
    }

    private List<ReplacementDetails> calculateReplacementDetails(Map<String, Set<String>> healthyInstances, long defaultTimeout) {
        return healthyInstances.entrySet()
                .stream()
                .map(entry -> {
                    val appId = entry.getKey();
                    val instances = entry.getValue();
                    val timeoutMultiplier = Math.max(1, instances.size() / defaultClusterOpSpec.getParallelism());
                    val computedTimeout = applicationEngine.getStateDB()
                            .application(appId)
                            .map(ApplicationInfo::getSpec)
                            .map(spec -> timeoutMultiplier * (ControllerUtils.maxStartTimeout(spec) + ControllerUtils.maxStopTimeout(spec)))
                            .orElse(defaultTimeout);
                    log.debug("Calculated timeout for app {} with {} instances is {} ms. Default: {} ms. Parallelism: {}. Timeout multipler: {}",
                            appId, instances, computedTimeout, defaultTimeout, defaultClusterOpSpec.getParallelism(), timeoutMultiplier);
                    val waitTime = computedTimeout + 30_000; //Adding some buffer to the wait time
                    return new ReplacementDetails(appId, instances, waitTime, computedTimeout);
                })
                .collect(Collectors.toUnmodifiableList());
    }

    private void moveAppsFromExecutors(final Set<String> executorIds, List<ReplacementDetails> replacementDetails) {
        val completionService = new ExecutorCompletionService<Pair<String, Boolean>>(appMovementExecutor);
        //Raise replacement request for multiple apps in parallel
        //Wait for all apps to submit successfully
        //Then poll for executors to have become empty
        val futures = new ArrayList<Future<Pair<String, Boolean>>>();
        val maxTimeToWait = new AtomicLong(0);
        replacementDetails
            .forEach(replacement -> {
                    val replacementFuture = completionService.submit(() -> issueReplaceCommand(replacement.waitTime(),
                                                                       replacement.appId(),
                                                                       replacement.instances(),
                                                                       replacement.operationTimeout()));
                    futures.add(replacementFuture);
                    maxTimeToWait.updateAndGet(current -> Math.max(current, replacement.operationTimeout()));
                });
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
            log.info("Commands accepted for all relevant app instances to be moved. Will wait for {} ms to ensure completion.", totalWait);
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
            log.info("Issuing replace command for app {} for instances {} with timeout {} ms. Will wait up to {} ms for command acceptance",
                    appId, instances, opTimeout, waitTime);
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
