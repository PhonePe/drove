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

import com.google.common.annotations.VisibleForTesting;
import com.phonepe.drove.common.discovery.Constants;
import com.phonepe.drove.common.model.utils.Pair;
import com.phonepe.drove.controller.engine.ApplicationLifecycleManagentEngine;
import com.phonepe.drove.controller.engine.ValidationStatus;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.ops.ApplicationReplaceInstancesOperation;
import io.dropwizard.lifecycle.Managed;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeException;
import dev.failsafe.RetryPolicy;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.phonepe.drove.common.CommonUtils.waitForAction;

/**
 * Moves instances from blacklisted hosts in a sequential manner to ensure app commands do not fail due to being busy
 */
@Order(70)
@Slf4j
@Singleton
public class BlacklistingAppMovementManager implements Managed {
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

    private final ApplicationLifecycleManagentEngine applicationEngine;
    private final ClusterResourcesDB clusterResourcesDB;

    private final RetryPolicy<ValidationStatus> opSubmissionPolicy;

    private final RetryPolicy<Boolean> noInstanceEnsurerPolicy;
    private final ClusterOpSpec defaultClusterOpSpec;
    private final long initialWaitTime;
    private final Future<?> future;

    private final Timer checkAfterExecutorRefreshTimer = new Timer();

    @Inject
    public BlacklistingAppMovementManager(
            LeadershipEnsurer leadershipEnsurer,
            ApplicationLifecycleManagentEngine applicationEngine,
            ClusterResourcesDB clusterResourcesDB,
            ClusterOpSpec defaultClusterOpSpec,
            @Named("BlacklistingAppMover") ExecutorService appMovementExecutor) {
        this(leadershipEnsurer,
             applicationEngine,
             clusterResourcesDB,
             DEFAULT_COMMAND_POLICY,
             RetryPolicy.<Boolean>builder()
                     .onFailedAttempt(event -> log.warn("Executor check attempt: {}", event.getAttemptCount()))
                     .handleResult(false)
                     .withMaxAttempts(-1)
                     .withMaxDuration(defaultClusterOpSpec.getTimeout()
                                              .toJavaDuration()
                                              .plus(Duration.ofSeconds(30))) //Wait for max app operation timeout and
                     // then some
                     .withDelay(10, 30, ChronoUnit.SECONDS)
                     .build(), //
             defaultClusterOpSpec,
             appMovementExecutor,
             Constants.EXECUTOR_REFRESH_INTERVAL.toMillis() * 2 + 5); //Wait till whole cluster refreshes at least once
    }

    @SuppressWarnings("java:S107")
    @VisibleForTesting
    BlacklistingAppMovementManager(
            LeadershipEnsurer leadershipEnsurer,
            ApplicationLifecycleManagentEngine applicationEngine,
            ClusterResourcesDB clusterResourcesDB,
            RetryPolicy<ValidationStatus> opSubmissionPolicy,
            RetryPolicy<Boolean> noInstanceEnsurerPolicy,
            ClusterOpSpec defaultClusterOpSpec,
            ExecutorService appMovementExecutor,
            long initialWaitTime) {
        this.appMovementExecutor = appMovementExecutor;
        this.applicationEngine = applicationEngine;
        this.clusterResourcesDB = clusterResourcesDB;
        this.opSubmissionPolicy = opSubmissionPolicy;
        this.noInstanceEnsurerPolicy = noInstanceEnsurerPolicy;
        this.defaultClusterOpSpec = defaultClusterOpSpec;
        this.initialWaitTime = initialWaitTime;
        this.future = queuePollingExecutor.submit(this::processQueuedElement);
        leadershipEnsurer.onLeadershipStateChanged().connect(this::handleLeadershipChanged);
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

    @SneakyThrows
    public boolean moveApps(final Set<String> executorIds) {
        lock.lock();
        try {
            val status = processing.addAll(executorIds);
            if (status) {
                condition.signalAll();
            }
            else {
                log.info("Did not schedule executors for app movement. Looks like app movement is already underway.");
            }
            return status;
        }
        finally {
            lock.unlock();
        }
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
        val futures = healthyInstances.entrySet()
                .stream()
                .map(entry -> {
                    val appId = entry.getKey();
                    val instances = entry.getValue();
                    return completionService.submit(() -> {
                        try {
                            val finalStatus = Failsafe.with(opSubmissionPolicy)
                                    .get(() -> {
                                        val res = applicationEngine.handleOperation(
                                                new ApplicationReplaceInstancesOperation(appId,
                                                                                         instances,
                                                                                         false,
                                                                                         defaultClusterOpSpec));
                                        log.info("Instances to be replaced for {}: {}. command acceptance status: {}",
                                                appId, instances, res);
                                        return res.getStatus();
                                    });
                            return new Pair<>(appId, finalStatus == ValidationStatus.SUCCESS);
                        }
                        catch (FailsafeException e) {
                            log.info("Failed to send command for app movement for: " + appId, e);
                        }
                        return new Pair<>(appId, false);
                    });
                })
                .toList();

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
                      executorIds,
                      failedApps);
        }
        else {
            log.info("Commands accepted for all relevant app instances to be moved");
            try {
                val allClear = waitForAction(noInstanceEnsurerPolicy,
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
        queuePollingExecutor.shutdown();
        log.info("Blacklisting manager stopped");
    }
}
