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

import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.controller.engine.ApplicationEngine;
import com.phonepe.drove.controller.engine.ValidationResult;
import com.phonepe.drove.controller.engine.ValidationStatus;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.operation.ops.ApplicationReplaceInstancesOperation;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import dev.failsafe.RetryPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.phonepe.drove.controller.ControllerTestUtils.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link BlacklistingAppMovementManager}
 */
@Slf4j
class BlacklistingAppMovementManagerTest {
    @Test
    @SneakyThrows
    void testBasicFlow() {
        val spec = appSpec(1);
        val instance = generateInstanceInfo(ControllerUtils.deployableObjectId(spec), spec, 0);
        val executor = executorHost(8080, List.of(instance), List.of());
        val noInstanceEx = executorHost(8080);
        val called = new AtomicBoolean();

        val applicationEngine = mock(ApplicationEngine.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        when(clusterResourcesDB.currentSnapshot(false)).thenAnswer(invocationMock -> called.get()
                                                                                     ? List.of(noInstanceEx)
                                                                                     : List.of(executor));
        when(clusterResourcesDB.currentSnapshot(anyString())).thenReturn(Optional.of(executor));

        when(applicationEngine.handleOperation(any(ApplicationReplaceInstancesOperation.class)))
                .thenAnswer(invocationMock -> {
                    called.set(true);
                    return ValidationResult.success();
                });
        val le = mock(LeadershipEnsurer.class);
        val s = new ConsumingSyncSignal<Boolean>();
        when(le.onLeadershipStateChanged()).thenReturn(s);

        val bmm = new BlacklistingAppMovementManager(le,
                                                     applicationEngine,
                                                     clusterResourcesDB,
                                                     DEFAULT_CLUSTER_OP,
                                                     Executors.newSingleThreadExecutor());
        bmm.start();

        bmm.moveApps(Set.of(executor.getExecutorId()));

        CommonTestUtils.waitUntil(called::get);
        bmm.stop();
    }

    @Test
    @SneakyThrows
    void testLeadershipChanged() {
        val spec = appSpec(1);
        val instance = generateInstanceInfo(ControllerUtils.deployableObjectId(spec), spec, 0);
        val executor = executorHost(1, 8080, List.of(instance), List.of(), true);
        val noInstanceEx = executorHost(1, 8080, List.of(), List.of(), true);
        val called = new AtomicBoolean();

        val applicationEngine = mock(ApplicationEngine.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        when(clusterResourcesDB.currentSnapshot(anyBoolean()))
                .thenAnswer(invocationMock -> called.get()
                                              ? List.of(noInstanceEx)
                                              : List.of(executor));
        when(clusterResourcesDB.currentSnapshot(anyString())).thenReturn(Optional.of(executor));
        when(clusterResourcesDB.isBlacklisted(executor.getExecutorId())).thenReturn(true);
        when(applicationEngine.handleOperation(any(ApplicationReplaceInstancesOperation.class)))
                .thenAnswer(invocationMock -> {
                    called.set(true);
                    return ValidationResult.success();
                });
        val le = mock(LeadershipEnsurer.class);
        val s = new ConsumingSyncSignal<Boolean>();
        when(le.onLeadershipStateChanged()).thenReturn(s);

        val bmm = new BlacklistingAppMovementManager(le,
                                                     applicationEngine,
                                                     clusterResourcesDB,
                                                     BlacklistingAppMovementManager.DEFAULT_COMMAND_POLICY,
                                                     RetryPolicy.<Boolean>builder()
                                                             .onFailedAttempt(event -> log.warn("Executor check attempt: {}", event.getAttemptCount()))
                                                             .handleResult(false)
                                                             .withMaxAttempts(-1)
                                                             .withMaxDuration(Duration.ofSeconds(60))
                                                             .withDelay(1, 5, ChronoUnit.SECONDS)
                                                             .build(),
                                                     DEFAULT_CLUSTER_OP,
                                                     Executors.newSingleThreadExecutor(),
                                                     100);
        bmm.start();

//        bmm.moveApps(Set.of(executor.getExecutorId()));
        le.onLeadershipStateChanged().dispatch(true);
        CommonTestUtils.waitUntil(called::get);
        bmm.stop();
    }

    @Test
    @SneakyThrows
    void testCompletionWait() {
        val spec = appSpec(1);
        val instance = generateInstanceInfo(ControllerUtils.deployableObjectId(spec), spec, 0);
        val executor = executorHost(8080, List.of(instance), List.of());
        val noInstanceEx = executorHost(8080);
        val called = new AtomicInteger();

        val applicationEngine = mock(ApplicationEngine.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        when(clusterResourcesDB.currentSnapshot(false)).thenAnswer(invocationMock -> called.incrementAndGet() > 2
                                                                                     ? List.of(noInstanceEx)
                                                                                     : List.of(executor));
        when(clusterResourcesDB.currentSnapshot(anyString())).thenReturn(Optional.of(executor));

        when(applicationEngine.handleOperation(any(ApplicationReplaceInstancesOperation.class)))
                .thenReturn(ValidationResult.success());
        val le = mock(LeadershipEnsurer.class);
        val s = new ConsumingSyncSignal<Boolean>();
        when(le.onLeadershipStateChanged()).thenReturn(s);

        val bmm = new BlacklistingAppMovementManager(le,
                                                     applicationEngine,
                                                     clusterResourcesDB,
                                                     BlacklistingAppMovementManager.DEFAULT_COMMAND_POLICY,
                                                     RetryPolicy.<Boolean>builder()
                                                             .onFailedAttempt(event -> log.warn(
                                                                     "Executor check attempt: {}",
                                                                     event.getAttemptCount()))
                                                             .handleResult(false)
                                                             .withMaxAttempts(5)
                                                             .withDelay(Duration.ofMillis(100))
                                                             .withMaxDuration(Duration.ofSeconds(1))
                                                             .build(),
                                                     DEFAULT_CLUSTER_OP,
                                                     Executors.newSingleThreadExecutor(),
                                                     100);
        bmm.start();

        bmm.moveApps(Set.of(executor.getExecutorId()));

        CommonTestUtils.waitUntil(() -> called.get() > 1);
        bmm.stop();
    }

    @Test
    @SneakyThrows
    void testCompletionWaitFail() {
        val spec = appSpec(1);
        val instance = generateInstanceInfo(ControllerUtils.deployableObjectId(spec), spec, 0);
        val executor = executorHost(8080, List.of(instance), List.of());
        val called = new AtomicInteger();

        val applicationEngine = mock(ApplicationEngine.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        when(clusterResourcesDB.currentSnapshot(false)).thenAnswer(invocationMock ->
                                                                   {
                                                                       called.incrementAndGet();
                                                                       return List.of(executor);
                                                                   });
        when(clusterResourcesDB.currentSnapshot(anyString())).thenReturn(Optional.of(executor));

        when(applicationEngine.handleOperation(any(ApplicationReplaceInstancesOperation.class)))
                .thenReturn(ValidationResult.success());
        val le = mock(LeadershipEnsurer.class);
        val s = new ConsumingSyncSignal<Boolean>();
        when(le.onLeadershipStateChanged()).thenReturn(s);

        val bmm = new BlacklistingAppMovementManager(le,
                                                     applicationEngine,
                                                     clusterResourcesDB,
                                                     BlacklistingAppMovementManager.DEFAULT_COMMAND_POLICY,
                                                     RetryPolicy.<Boolean>builder()
                                                             .onFailedAttempt(event -> log.warn(
                                                                     "Executor check attempt: {}",
                                                                     event.getAttemptCount()))
                                                             .handleResult(false)
                                                             .withMaxAttempts(2)
                                                             .withDelay(Duration.ofMillis(100))
                                                             .withMaxDuration(Duration.ofSeconds(1))
                                                             .build(),
                                                     DEFAULT_CLUSTER_OP,
                                                     Executors.newSingleThreadExecutor(),
                                                     100);
        bmm.start();

        bmm.moveApps(Set.of(executor.getExecutorId()));

        CommonTestUtils.waitUntil(() -> called.get() > 1);
        bmm.stop();
    }

    @Test
    @SneakyThrows
    void testCommandRetry() {
        val spec = appSpec(1);
        val instance = generateInstanceInfo(ControllerUtils.deployableObjectId(spec), spec, 0);
        val executor = executorHost(8080, List.of(instance), List.of());
        val noInstanceEx = executorHost(8080);
        val called = new AtomicBoolean();
        val count = new AtomicInteger();
        val applicationEngine = mock(ApplicationEngine.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        when(clusterResourcesDB.currentSnapshot(false)).thenAnswer(invocationMock -> called.get()
                                                                                     ? List.of(noInstanceEx)
                                                                                     : List.of(executor));
        when(clusterResourcesDB.currentSnapshot(anyString())).thenReturn(Optional.of(executor));

        when(applicationEngine.handleOperation(any(ApplicationReplaceInstancesOperation.class)))
                .thenAnswer(invocationMock -> {
                    if (count.incrementAndGet() > 1) {
                        called.set(true);
                        return ValidationResult.success();
                    }
                    return ValidationResult.failure("Test failure");
                });
        val le = mock(LeadershipEnsurer.class);
        val s = new ConsumingSyncSignal<Boolean>();
        when(le.onLeadershipStateChanged()).thenReturn(s);

        val bmm = new BlacklistingAppMovementManager(le, applicationEngine, clusterResourcesDB,
                                                     RetryPolicy.<ValidationStatus>builder()
                                                             .onFailedAttempt(event -> log.warn(
                                                                     "Command submission attempt: {}",
                                                                     event.getAttemptCount()))
                                                             .handleResult(ValidationStatus.FAILURE)
                                                             .withMaxAttempts(10)
                                                             .withDelay(Duration.ofMillis(100))
                                                             .build(),
                                                     BlacklistingAppMovementManager.DEFAULT_COMPLETION_POLICY,
                                                     DEFAULT_CLUSTER_OP,
                                                     Executors.newSingleThreadExecutor(),
                                                     100);
        bmm.start();

        bmm.moveApps(Set.of(executor.getExecutorId()));

        CommonTestUtils.waitUntil(called::get);
        bmm.stop();
    }
}