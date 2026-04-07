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

import static com.phonepe.drove.controller.ControllerTestUtils.appSpec;
import static com.phonepe.drove.controller.ControllerTestUtils.executorHost;
import static com.phonepe.drove.controller.ControllerTestUtils.generateInstanceInfo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.codahale.metrics.SharedMetricRegistries;
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
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.InMemoryClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.deploy.FailureStrategy;
import com.phonepe.drove.models.operation.ops.ApplicationReplaceInstancesOperation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.appform.functionmetrics.FunctionMetricsManager;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.SneakyThrows;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

/**
 * Tests {@link BlacklistingManager}
 */
@Slf4j
class BlacklistingManagerTest {
    @BeforeAll
    static void setup() {
        FunctionMetricsManager.initialize(BlacklistingManagerTest.class.getPackageName(), SharedMetricRegistries.getOrCreate("test"));
    }

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
        val blacklistedIds = bmm.blacklistExecutors(executorIds).getSuccessful();
        assertEquals(5, blacklistedIds.size());
        assertEquals(blacklistedIds, clusterResourcesDB.blacklistedNodes());

        CommonTestUtils.waitUntil(() -> {
            val unblacklistedIds = bmm.unblacklistExecutors(executorIds).getSuccessful();
            if(null != unblacklistedIds) {
                assertEquals(5, unblacklistedIds.size());
                return true;
            }
            return false;
        });
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
        val applicationStateDB = mock(ApplicationStateDB.class);
        when(applicationEngine.getStateDB()).thenReturn(applicationStateDB);
        when(applicationStateDB.application(anyString()))
            .thenReturn(Optional.of(new ApplicationInfo(instance.getAppId(), spec, 1, null, null)));
        val le = mock(LeadershipEnsurer.class);
        val s = new ConsumingSyncSignal<Boolean>();
        when(le.onLeadershipStateChanged()).thenReturn(s);
        val communicator = mock(ControllerCommunicator.class);
        when(communicator.send(any(ExecutorMessage.class)))
            .thenReturn(new MessageResponse(MessageHeader.controllerRequest(), MessageDeliveryStatus.ACCEPTED));
        val eventBus = new DroveEventBus();
        val bmm = new BlacklistingManager(le,
                                          applicationEngine,
                                          clusterResourcesDB,
                                          communicator,
                                          eventBus,
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
        val executor = executorHost(1, 8080, List.of(instance), List.of(), List.of(), ExecutorState.BLACKLIST_REQUESTED);
        val noInstanceEx = executorHost(1, 8080, List.of(), List.of(), List.of(), ExecutorState.BLACKLIST_REQUESTED);
        val called = new AtomicBoolean();

        val applicationEngine = mock(ApplicationLifecycleManagementEngine.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        when(clusterResourcesDB.currentSnapshot(anyBoolean()))
                .thenAnswer(invocationMock -> called.get()
                                              ? List.of(noInstanceEx)
                                              : List.of(executor));
        when(clusterResourcesDB.currentSnapshot(anyString())).thenReturn(Optional.of(executor));
        when(applicationEngine.handleOperation(any(ApplicationReplaceInstancesOperation.class)))
                .thenAnswer(invocationMock -> {
                    called.set(true);
                    return ValidationResult.success();
                });
        val applicationStateDB = mock(ApplicationStateDB.class);
        when(applicationEngine.getStateDB()).thenReturn(applicationStateDB);
        when(applicationStateDB.application(anyString()))
            .thenReturn(Optional.of(new ApplicationInfo(instance.getAppId(), spec, 1, null, null)));
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
                                          clusterOpSpec(),
                                          Executors.newSingleThreadExecutor(),
                                          100);
        bmm.start();

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
        val applicationStateDB = mock(ApplicationStateDB.class);
        when(applicationEngine.getStateDB()).thenReturn(applicationStateDB);
        when(applicationStateDB.application(anyString()))
            .thenReturn(Optional.of(new ApplicationInfo(instance.getAppId(), spec, 1, null, null)));
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
        val asDB = mock(ApplicationStateDB.class);
        when(applicationEngine.getStateDB()).thenReturn(asDB);
        when(asDB.application(anyString())).thenReturn(Optional.of(new ApplicationInfo(instance.getAppId(), spec, 1, null, null)));
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        when(clusterResourcesDB.currentSnapshot(false)).thenAnswer(invocationMock ->
                                                                   {
                                                                       called.incrementAndGet();
                                                                       return List.of(executor);
                                                                   });
        when(clusterResourcesDB.currentSnapshot(anyString())).thenReturn(Optional.of(executor));

        when(applicationEngine.handleOperation(any(ApplicationReplaceInstancesOperation.class)))
                .thenReturn(ValidationResult.success());
        val applicationStateDB = mock(ApplicationStateDB.class);
        when(applicationEngine.getStateDB()).thenReturn(applicationStateDB);
        when(applicationStateDB.application(anyString()))
            .thenReturn(Optional.of(new ApplicationInfo(instance.getAppId(), spec, 1, null, null)));
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
        val applicationStateDB = mock(ApplicationStateDB.class);
        when(applicationEngine.getStateDB()).thenReturn(applicationStateDB);
        when(applicationStateDB.application(anyString()))
            .thenReturn(Optional.of(new ApplicationInfo(instance.getAppId(), spec, 1, null, null)));
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
    void testSendExecutorMessageFail() {
        val executor = executorHost(8080);
        val le = mock(LeadershipEnsurer.class);
        val s = new ConsumingSyncSignal<Boolean>();
        when(le.onLeadershipStateChanged()).thenReturn(s);
        val applicationEngine = mock(ApplicationLifecycleManagementEngine.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        when(clusterResourcesDB.currentSnapshot(anyString())).thenReturn(Optional.of(executor));
        val communicator = mock(ControllerCommunicator.class);
        when(communicator.send(any(ExecutorMessage.class)))
                .thenReturn(new MessageResponse(MessageHeader.controllerRequest(), MessageDeliveryStatus.FAILED));
        val eventBus = new DroveEventBus();
        val bmm = new BlacklistingManager(le,
                                          applicationEngine,
                                          clusterResourcesDB,
                                          communicator,
                                          eventBus,
                                          clusterOpSpec(),
                                          Executors.newSingleThreadExecutor(),
                                          100);
        bmm.start();
        val result = bmm.blacklistExecutors(Set.of(executor.getExecutorId()));
        assertTrue(result.getSuccessful().isEmpty());
        bmm.stop();
    }

    @Test
    @SneakyThrows
    void testBlacklistAlreadyBlacklisted() {
        val executor = executorHost(1, 8080, List.of(), List.of(), List.of(), ExecutorState.BLACKLISTED);
        val le = mock(LeadershipEnsurer.class);
        val s = new ConsumingSyncSignal<Boolean>();
        when(le.onLeadershipStateChanged()).thenReturn(s);
        val applicationEngine = mock(ApplicationLifecycleManagementEngine.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        when(clusterResourcesDB.currentSnapshot(anyString())).thenReturn(Optional.of(executor));
        val communicator = mock(ControllerCommunicator.class);
        val eventBus = new DroveEventBus();
        val bmm = new BlacklistingManager(le,
                                          applicationEngine,
                                          clusterResourcesDB,
                                          communicator,
                                          eventBus,
                                          clusterOpSpec(),
                                          Executors.newSingleThreadExecutor(),
                                          100);
        bmm.start();
        val result = bmm.blacklistExecutors(Set.of(executor.getExecutorId()));
        assertTrue(result.getSuccessful().isEmpty());
        bmm.stop();
    }

    @Test
    @SneakyThrows
    void testBlacklistExecutorNotFound() {
        val le = mock(LeadershipEnsurer.class);
        val s = new ConsumingSyncSignal<Boolean>();
        when(le.onLeadershipStateChanged()).thenReturn(s);
        val applicationEngine = mock(ApplicationLifecycleManagementEngine.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        when(clusterResourcesDB.currentSnapshot(anyString())).thenReturn(Optional.empty());
        val communicator = mock(ControllerCommunicator.class);
        val eventBus = new DroveEventBus();
        val bmm = new BlacklistingManager(le,
                                          applicationEngine,
                                          clusterResourcesDB,
                                          communicator,
                                          eventBus,
                                          clusterOpSpec(),
                                          Executors.newSingleThreadExecutor(),
                                          100);
        bmm.start();
        val result = bmm.blacklistExecutors(Set.of("unknown-executor"));
        assertTrue(result.getSuccessful().isEmpty());
        bmm.stop();
    }

    private static ClusterOpSpec clusterOpSpec() {
        return new ClusterOpSpec(io.dropwizard.util.Duration.milliseconds(100), 1, FailureStrategy.STOP);
    }

}
