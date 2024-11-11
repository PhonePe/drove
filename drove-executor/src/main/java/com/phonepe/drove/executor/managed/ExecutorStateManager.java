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

package com.phonepe.drove.executor.managed;

import com.phonepe.drove.executor.discovery.ClusterClient;
import com.phonepe.drove.executor.engine.LocalServiceInstanceEngine;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeException;
import dev.failsafe.RetryPolicy;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 *
 */
@Slf4j
@Singleton
public class ExecutorStateManager implements Managed {
    private final AtomicReference<ExecutorState> currentState = new AtomicReference<>(ExecutorState.ACTIVE);
    private final ConsumingFireForgetSignal<ExecutorState> stateChanged = new ConsumingFireForgetSignal<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final LocalServiceInstanceEngine localServiceInstanceEngine;
    private final ClusterClient clusterClient;

    @Inject
    public ExecutorStateManager(LocalServiceInstanceEngine localServiceInstanceEngine,
                                ClusterClient clusterClient) {
        this.localServiceInstanceEngine = localServiceInstanceEngine;
        this.clusterClient = clusterClient;
    }

    public void blacklist() {
        updateState(ExecutorState.BLACKLISTED);
    }

    public void unblacklist() {
        updateState(ExecutorState.UNREADY);
    }

    public ExecutorState currentState() {
        return currentState.get();
    }

    public boolean isBlacklisted() {
        return ExecutorState.BLACKLISTED.equals(currentState.get());
    }

    public ConsumingFireForgetSignal<ExecutorState> onStateChange() {
        return stateChanged;
    }

    @Override
    public void start() throws Exception {
        stateChanged.connect(state -> {
            if(state.equals(ExecutorState.UNREADY)) {
                log.info("Executor entered unready state, will check and activate");
                executorService.submit(this::ensureActive);
            }
        });
        log.info("Setting initial state to unready");
        updateState(ExecutorState.UNREADY);
    }

    @Override
    public void stop() throws Exception {
        Managed.super.stop();
    }

    private void updateState(ExecutorState state) {
        currentState.set(state);
        stateChanged.dispatch(state);
        log.info("Executor state updated to {}", state);
    }

    private void ensureActive() {
        val requiredResources = clusterClient.reservedResources();

        val retryPolicy = RetryPolicy.<Boolean>builder()
                .withDelay(Duration.ofSeconds(1))
                .withMaxAttempts(-1)
                .onFailedAttempt(event -> {
                    if(event.getLastException() != null) {
                        log.error("Attempt: {} : Local service instances check failed with error: {}",
                                  event.getAttemptCount(), event.getLastException().getMessage());
                    }
                    else {
                        log.warn("Attempt: {} : Local service instances not yet spawned.",
                                 event.getAttemptCount());
                    }
                })
                .handleResultIf(r -> !r)
                .build();
        try {
            Failsafe.with(retryPolicy)
                    .get(() -> {
                        val currCounts = localServiceInstanceEngine.currentState()
                                .stream()
                                .collect(Collectors.groupingBy(LocalServiceInstanceInfo::getServiceId, Collectors.counting()));
                        return requiredResources.getRequiredInstances()
                                .entrySet()
                                .stream()
                                .allMatch(entry -> currCounts.getOrDefault(entry.getKey(), 0L)
                                        .equals((long) entry.getValue()));
                    });
        }
        catch (FailsafeException e) {
            log.error("Error determining state: ", e);
        }
        log.info("All required local service instances present. Activating executor");
        updateState(ExecutorState.ACTIVE);
    }
}
