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
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.executor.BlacklistExecutorMessage;
import com.phonepe.drove.common.model.executor.ExecutorMessage;
import com.phonepe.drove.common.model.executor.UnBlacklistExecutorMessage;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.engine.ApplicationLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.ValidationResult;
import com.phonepe.drove.controller.engine.ValidationStatus;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.InMemoryClusterResourcesDB;
import com.phonepe.drove.controller.testsupport.InMemoryClusterStateDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.deploy.FailureStrategy;
import com.phonepe.drove.models.operation.ops.ApplicationReplaceInstancesOperation;
import dev.failsafe.RetryPolicy;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.phonepe.drove.controller.ControllerTestUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link BlacklistingManager}
 */
@Slf4j
class BlacklistingManagerTest {
    @Test
    @SneakyThrows
    void testTopLevel() {
        val numExecutors = 5;
        val le = mock(LeadershipEnsurer.class);
        val s = new ConsumingSyncSignal<Boolean>();
        when(le.onLeadershipStateChanged()).thenReturn(s);
        val applicationEngine = mock(ApplicationLifecycleManagementEngine.class);
        val clusterResourcesDB = new InMemoryClusterResourcesDB();
        val communicator = mock(ControllerCommunicator.class);
        val acceptedCount = new AtomicInteger(0);
        when(communicator.send(any(ExecutorMessage.class)))
            .thenAnswer(param -> {
                val message = param.getArgument(0, ExecutorMessage.class);
                if(message instanceof BlacklistExecutorMessage) {
                    if(5 == acceptedCount.incrementAndGet()) {
                        clusterResourcesDB.update(IntStream.range(0, numExecutors)
                                .mapToObj(i -> ControllerTestUtils.generateExecutorNode(i, Set.of(), true))
                                .toList());
                    }
                }
                if(message instanceof UnBlacklistExecutorMessage) {
                    if(0 == acceptedCount.decrementAndGet()) {
                        clusterResourcesDB.update(IntStream.range(0, numExecutors)
                                .mapToObj(i -> ControllerTestUtils.generateExecutorNode(i, Set.of(), false))
                                .toList());
                    }
                }
                return new MessageResponse(MessageHeader.controllerRequest(), MessageDeliveryStatus.ACCEPTED);
            });
        val eventBus = new DroveEventBus();
        val bmm = new BlacklistingManager(le,
                                          applicationEngine,
                                          clusterResourcesDB,
                                          communicator,
                                          eventBus,
                                          opSubmissionPolicy(),
                                          checkPolicy(),
                                          clusterOpSpec(),
                                          Executors.newSingleThreadExecutor(),
                                          100);
        val unblackListedExecutors = IntStream.range(0, 5)
                .mapToObj(i -> ControllerTestUtils.generateExecutorNode(i, Set.of(), false))
                .toList();
        clusterResourcesDB.update(unblackListedExecutors);
        bmm.start();
        CommonTestUtils.delay(Duration.ofMillis(100));
        assertTrue(clusterResourcesDB.blacklistedNodes().isEmpty());
        val executorIds = unblackListedExecutors.stream()
            .map(node -> node.getState().getExecutorId())
            .collect(Collectors.toUnmodifiableSet());
        val blacklistedIds = bmm.blacklistExecutors(executorIds);
        assertEquals(5, blacklistedIds.size());
        assertEquals(blacklistedIds, clusterResourcesDB.blacklistedNodes());

        val unblacklistedIds = bmm.unblacklistExecutors(executorIds);
        assertEquals(5, unblacklistedIds.size());
        assertTrue(clusterResourcesDB.blacklistedNodes().isEmpty());
        bmm.stop();
    }

    @Test
    @SneakyThrows
    void testBasicFlow() {
        val spec = appSpec(1);
        val instance = generateInstanceInfo(ControllerUtils.deployableObjectId(spec), spec, 0);
        val executor = executorHost(8080, List.of(instance), List.of(), List.of());
        val noInstanceEx = executorHost(8080);
        val called = new AtomicBoolean();

        val applicationEngine = mock(ApplicationLifecycleManagementEngine.class);
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
        val communicator = mock(ControllerCommunicator.class);
        when(communicator.send(any(ExecutorMessage.class))).thenReturn(new MessageResponse(MessageHeader.controllerRequest(), MessageDeliveryStatus.ACCEPTED));
        val eventBus = new DroveEventBus();
        val bmm = new BlacklistingManager(le,
                                          applicationEngine,
                                          clusterResourcesDB,
                                          communicator,
                                          eventBus,
                                          opSubmissionPolicy(),
                                          checkPolicy(),
                                          clusterOpSpec(),
                                          Executors.newSingleThreadExecutor(),
                                          100);
        bmm.start();
        CommonTestUtils.delay(Duration.ofMillis(100));

        bmm.moveApps(Set.of(executor.getExecutorId()));

        CommonTestUtils.waitUntil(called::get);
        bmm.stop();
    }

    @Test
    @SneakyThrows
    void testLeadershipChanged() {
        val spec = appSpec(1);
        val instance = generateInstanceInfo(ControllerUtils.deployableObjectId(spec), spec, 0);
        val executor = executorHost(1, 8080, List.of(instance), List.of(), List.of(), true);
        val noInstanceEx = executorHost(1, 8080, List.of(), List.of(), List.of(), true);
        val called = new AtomicBoolean();

        val applicationEngine = mock(ApplicationLifecycleManagementEngine.class);
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

        val communicator = mock(ControllerCommunicator.class);
        when(communicator.send(any(ExecutorMessage.class))).thenReturn(new MessageResponse(MessageHeader.controllerRequest(), MessageDeliveryStatus.ACCEPTED));
        val eventBus = new DroveEventBus();
        val bmm = new BlacklistingManager(le,
                                          applicationEngine,
                                          clusterResourcesDB,
                                          communicator,
                                          eventBus,
                                          opSubmissionPolicy(),
                                          checkPolicy(),
                                          clusterOpSpec(),
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
        val executor = executorHost(8080, List.of(instance), List.of(), List.of());
        val noInstanceEx = executorHost(8080);
        val called = new AtomicInteger();

        val applicationEngine = mock(ApplicationLifecycleManagementEngine.class);
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

        val communicator = mock(ControllerCommunicator.class);
        when(communicator.send(any(ExecutorMessage.class))).thenReturn(new MessageResponse(MessageHeader.controllerRequest(), MessageDeliveryStatus.ACCEPTED));
        val eventBus = new DroveEventBus();
        val bmm = new BlacklistingManager(le,
                                          applicationEngine,
                                          clusterResourcesDB,
                                          communicator,
                                          eventBus,
                                          opSubmissionPolicy(),
                                          checkPolicy(),
                                          clusterOpSpec(),
                                          Executors.newSingleThreadExecutor(),
                                          100);
        bmm.start();
        CommonTestUtils.delay(Duration.ofMillis(100));

        bmm.moveApps(Set.of(executor.getExecutorId()));

        CommonTestUtils.waitUntil(() -> called.get() > 1);
        bmm.stop();
    }

    @Test
    @SneakyThrows
    void testCompletionWaitFail() {
        val spec = appSpec(1);
        val instance = generateInstanceInfo(ControllerUtils.deployableObjectId(spec), spec, 0);
        val executor = executorHost(8080, List.of(instance), List.of(), List.of());
        val called = new AtomicInteger();

        val applicationEngine = mock(ApplicationLifecycleManagementEngine.class);
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

        val communicator = mock(ControllerCommunicator.class);
        when(communicator.send(any(ExecutorMessage.class))).thenReturn(new MessageResponse(MessageHeader.controllerRequest(), MessageDeliveryStatus.ACCEPTED));
        val eventBus = new DroveEventBus();
        val bmm = new BlacklistingManager(le,
                                          applicationEngine,
                                          clusterResourcesDB,
                                          communicator,
                                          eventBus,
                                          opSubmissionPolicy(),
                                          checkPolicy(),
                                          clusterOpSpec(),
                                          Executors.newSingleThreadExecutor(),
                                          100);
        bmm.start();
        CommonTestUtils.delay(Duration.ofMillis(100));

        bmm.moveApps(Set.of(executor.getExecutorId()));

        CommonTestUtils.waitUntil(() -> called.get() > 1);
        bmm.stop();
    }

    @Test
    @SneakyThrows
    void testCommandRetry() {
        val spec = appSpec(1);
        val instance = generateInstanceInfo(ControllerUtils.deployableObjectId(spec), spec, 0);
        val executor = executorHost(8080, List.of(instance), List.of(), List.of());
        val noInstanceEx = executorHost(8080);
        val called = new AtomicBoolean();
        val count = new AtomicInteger();
        val applicationEngine = mock(ApplicationLifecycleManagementEngine.class);
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

        val communicator = mock(ControllerCommunicator.class);
        when(communicator.send(any(ExecutorMessage.class))).thenReturn(new MessageResponse(MessageHeader.controllerRequest(), MessageDeliveryStatus.ACCEPTED));
        val eventBus = new DroveEventBus();
        val bmm = new BlacklistingManager(le,
                                          applicationEngine,
                                          clusterResourcesDB,
                                          communicator,
                                          eventBus,
                                          opSubmissionPolicy(),
                                          checkPolicy(),
                                          clusterOpSpec(),
                                          Executors.newSingleThreadExecutor(),
                                          100);
        bmm.start();
        CommonTestUtils.delay(Duration.ofMillis(100));
        bmm.moveApps(Set.of(executor.getExecutorId()));

        CommonTestUtils.waitUntil(called::get);
        bmm.stop();
    }

    private static ClusterOpSpec clusterOpSpec() {
        return new ClusterOpSpec(io.dropwizard.util.Duration.milliseconds(100), 1, FailureStrategy.STOP);
    }

    private static RetryPolicy<Boolean> checkPolicy() {
        return RetryPolicy.<Boolean>builder()
                .onFailedAttempt(event -> log.warn("Executor check attempt: {}", event.getAttemptCount()))
                .handleResult(false)
                .withMaxAttempts(-1)
                .withDelay(Duration.ofMillis(100))
                .withMaxDuration(Duration.ofMinutes(3))
                .build();
    }

    private static RetryPolicy<ValidationStatus> opSubmissionPolicy() {
        return RetryPolicy.<ValidationStatus>builder()
                .onFailedAttempt(event -> log.warn(
                        "Command submission attempt: {}",
                        event.getAttemptCount()))
                .handleResult(ValidationStatus.FAILURE)
                .withMaxAttempts(10)
                .withDelay(Duration.ofMillis(100))
                .build();
    }
}
