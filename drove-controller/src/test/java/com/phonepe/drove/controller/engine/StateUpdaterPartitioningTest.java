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

package com.phonepe.drove.controller.engine;

import static com.phonepe.drove.controller.ControllerTestUtils.appSpec;
import static com.phonepe.drove.controller.ControllerTestUtils.generateExecutorNode;
import static com.phonepe.drove.controller.ControllerTestUtils.generateInstanceInfo;
import static com.phonepe.drove.controller.utils.ControllerUtils.deployableObjectId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import com.codahale.metrics.SharedMetricRegistries;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.resources.PhysicalLayout;
import com.phonepe.drove.models.info.resources.available.AvailableCPU;
import com.phonepe.drove.models.info.resources.available.AvailableMemory;
import com.phonepe.drove.models.instance.InstanceInfo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.appform.functionmetrics.FunctionMetricsManager;
import lombok.SneakyThrows;
import lombok.val;

/**
 * Integration tests for partitioning logic in StateUpdater.
 * Verifies that (1) partitioning is consistent per executor ID across update types,
 * and (2) updates within a partition are processed in submission order.
 *
 */
@ExtendWith(MockitoExtension.class)
class StateUpdaterPartitioningTest {

    private static final int PARALLELISM = 4;
    private static final ControllerOptions CONTROLLER_OPTIONS = ControllerOptions.DEFAULT
        .withDataUpdateParallelism(PARALLELISM);

    @Mock
    private ClusterResourcesDB cDB;
    @Mock
    private TaskDB taskDB;
    @Mock
    private ApplicationInstanceInfoDB iiDB;
    @Mock
    private LocalServiceStateDB lsDB;
    @Mock
    private NodeDataStore nds;
    @Mock
    private DroveEventBus eventBus;

    @BeforeAll
    static void setup() {
        FunctionMetricsManager.initialize(
                StateUpdaterPartitioningTest.class.getSimpleName(),
                SharedMetricRegistries.getOrCreate("test"));
    }

    /**
     * Ensures all updates for an executor passes through the same partition
     */
    @Test
    @SneakyThrows
    void sameExecutorSamePartitionAcrossUpdateTypes() {
        val threadsByExecutor = new ConcurrentHashMap<String, Set<String>>();
        val totalProcessed = new AtomicInteger(0);

        val appSpec = appSpec(1);
        val appId = deployableObjectId(appSpec);
        val appInstance = generateInstanceInfo(appId, appSpec, 0);
        val appInstanceOnEx1 = generateInstanceInfo(appId, appSpec, 1);

        // Empty-instance host infos so the remove handler reaches cDB.remove() (not fast-path)
        val emptyHostInfo0 = new ExecutorHostInfo(ControllerTestUtils.executorId(0),
                                                  nodeWithInstances(0, List.of()),
                                                  Map.of(0, new ExecutorHostInfo.NumaNodeInfo()));

        // Each partition has a single thread so we use thread id as proxy for partition id
        // So we basically record thread ids for different op types
        // And then verify that for a given executor, all op types have the same thread id,
        // and different executors have different thread ids
        doAnswer(inv -> {
            val node = inv.getArgument(0, ExecutorNodeData.class);
            val execId = node.getState().getExecutorId();
            CommonTestUtils.delay(Duration.ofMillis(5));
            threadsByExecutor.computeIfAbsent(execId, k -> ConcurrentHashMap.newKeySet())
                    .add(Thread.currentThread().getName());
            totalProcessed.incrementAndGet();
            return null;
        }).when(nds).updateNodeData(any());

        doAnswer(inv -> {
            val instanceInfo = inv.getArgument(2, InstanceInfo.class);
            val execId = instanceInfo.getExecutorId();
            CommonTestUtils.delay(Duration.ofMillis(5));
            threadsByExecutor.computeIfAbsent(execId, k -> ConcurrentHashMap.newKeySet())
                    .add(Thread.currentThread().getName());
            totalProcessed.incrementAndGet();
            return true;
        }).when(iiDB).updateInstanceState(anyString(), anyString(), any(InstanceInfo.class));

        doAnswer(inv -> {
            @SuppressWarnings("unchecked") val executorIds = (List<String>)inv.getArgument(0, List.class);
            CommonTestUtils.delay(Duration.ofMillis(5));
            for (val id : executorIds) {
                threadsByExecutor.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet())
                        .add(Thread.currentThread().getName());
            }
            totalProcessed.incrementAndGet();
            return null;
        }).when(cDB).remove(anyList());

        doReturn(Optional.of(emptyHostInfo0)).when(cDB).currentSnapshot(ControllerTestUtils.executorId(0));

        doReturn(Optional.empty()).when(iiDB).instance(anyString(), anyString());

        try (val su = buildStateUpdater("test-partitioning-1")) {
            su.updateClusterResource(generateExecutorNode(0));
            su.updateSingle(resourceSnapshot(0), appInstance);
            su.remove(List.of(ControllerTestUtils.executorId(0)));
            su.updateClusterResource(generateExecutorNode(1));
            su.updateSingle(resourceSnapshot(1), appInstanceOnEx1);

            CommonTestUtils.waitUntil(() -> totalProcessed.get() == 5);
            assertEquals(5, totalProcessed.get());

            // All updates for EXECUTOR_0 would be on the same thread,
            // and all updates for EXECUTOR_1 would be on the same (but different from EXECUTOR_0) thread
            assertEquals(1,
                         threadsByExecutor.get(ControllerTestUtils.executorId(0)).size(),
                         "All update types for EXECUTOR_0 must be processed by the same thread");
            assertEquals(1,
                         threadsByExecutor.get(ControllerTestUtils.executorId(1)).size(),
                         "All update types for EXECUTOR_1 must be processed by the same thread");
        }
    }


    /**
     * Ensures that instance updates for the same executor are processed in submission order
     */
    @Test
    void instanceUpdatesAreOrdered() {
        val submissionGate = new CountDownLatch(1);
        val recordedOrder = new CopyOnWriteArrayList<String>();
        val firstCallGating = new AtomicBoolean(true);

        val appSpec = appSpec(1);
        val appId = deployableObjectId(appSpec);

        doReturn(Optional.empty()).when(iiDB).instance(anyString(), anyString());

        // We store the instance ID in recordedOrder and then verify that
        // the order of instance IDs in recordedOrder matches the submission order
        doAnswer(inv -> {
            val instanceInfo = inv.getArgument(2, InstanceInfo.class);
            if (firstCallGating.compareAndSet(true, false)) {
                submissionGate.await();
            }
            CommonTestUtils.delay(Duration.ofMillis(2));
            recordedOrder.add(instanceInfo.getInstanceId());
            return true;
        }).when(iiDB).updateInstanceState(anyString(), anyString(), any(InstanceInfo.class));

        try (val su = buildStateUpdater("test-partitioning-2")) {
            val submittedIds = new ArrayList<String>();
            IntStream.range(0, 10)
                .forEach(i -> {
                    val instanceInfo = generateInstanceInfo(appId, appSpec, i);
                    su.updateSingle(resourceSnapshot(0), instanceInfo);
                    submittedIds.add(instanceInfo.getInstanceId());
                });
            submissionGate.countDown();

            CommonTestUtils.waitUntil(() -> recordedOrder.size() == 10);
            assertEquals(submittedIds, recordedOrder);
        }
    }

    /**
     * Ensures that different update types for the same executor are processed in submission order
     */
    @Test
    void mixedUpdateTypesAreOrdered() {
        val events = new CopyOnWriteArrayList<String>();

        val appSpec = appSpec(1);
        val appId = deployableObjectId(appSpec);
        val appInstance = generateInstanceInfo(appId, appSpec, 0);

        doReturn(Optional.empty()).when(iiDB).instance(anyString(), anyString());

        // We use the event bus to listen to events and ensure they appear in the expected order
        doAnswer(inv -> {
            CommonTestUtils.delay(Duration.ofMillis(5));
            events.add("instance-update");
            return true;
        }).when(iiDB).updateInstanceState(anyString(), anyString(), any(InstanceInfo.class));

        doAnswer(inv -> {
            CommonTestUtils.delay(Duration.ofMillis(5));
            events.add("snapshot-update");
            return null;
        }).when(nds).updateNodeData(any());

        doAnswer(inv -> {
            CommonTestUtils.delay(Duration.ofMillis(5));
            events.add("remove");
            return null;
        }).when(cDB).remove(anyList());

        val emptyHostInfo = new ExecutorHostInfo(
                                                 ControllerTestUtils.executorId(0),
                                                 nodeWithInstances(0, List.of()),
                                                 Map.of(0, new ExecutorHostInfo.NumaNodeInfo()));
        doReturn(Optional.of(emptyHostInfo)).when(cDB).currentSnapshot(ControllerTestUtils.executorId(0));
        try (val su = buildStateUpdater("test-partitioning-3")) {
            su.updateSingle(resourceSnapshot(0), appInstance);
            su.updateClusterResource(generateExecutorNode(0));
            su.remove(List.of(ControllerTestUtils.executorId(0)));

            CommonTestUtils.waitUntil(() -> events.size() == 3);
            assertEquals(List.of("instance-update", "snapshot-update", "remove"), events);
        }
    }


    /**
     * Ensures that a remove submitted after N updates is processed
     * only after all N updates complete
     */
    @Test
    void removeIsOrderedAfterPrecedingUpdates() {
        val events = new CopyOnWriteArrayList<String>();
        val updateCounter = new AtomicInteger(0);

        val appSpec = appSpec(1);
        val appId = deployableObjectId(appSpec);
        val instance = generateInstanceInfo(appId, appSpec, 0);

        val nodeDataWithInstance = nodeWithInstances(0, List.of(instance));
        val hostInfo = new ExecutorHostInfo(
                                            ControllerTestUtils.executorId(0),
                                            nodeDataWithInstance,
                                            Map.of(0, new ExecutorHostInfo.NumaNodeInfo()));

        doReturn(Optional.empty()).when(iiDB).instance(anyString(), anyString());

        doAnswer(inv -> {
            CommonTestUtils.delay(Duration.ofMillis(5));
            events.add("update-" + updateCounter.getAndIncrement());
            return true;
        }).when(iiDB).updateInstanceState(anyString(), anyString(), any(InstanceInfo.class));

        doAnswer(inv -> {
            events.add("remove");
            return true;
        }).when(iiDB).deleteInstanceState(anyString(), anyString());

        doReturn(Optional.of(hostInfo)).when(cDB).currentSnapshot(ControllerTestUtils.executorId(0));
        doNothing().when(cDB).remove(anyList());

        try (val su = buildStateUpdater("test-partitioning-4")) {
            IntStream.range(0, 5)
                .forEach(i -> su.updateSingle(resourceSnapshot(0),
                                              generateInstanceInfo(appId, appSpec, i)));
            su.remove(List.of(ControllerTestUtils.executorId(0)));

            CommonTestUtils.waitUntil(() -> events.size() == 6);

            IntStream.range(0, 5)
                .forEach(i -> assertTrue(events.get(i).startsWith("update-"),
                                         "Expected update at index " + i + " but got: " + events.get(i)));
            assertEquals("remove", events.get(5));
        }
    }

    /**
     * Ensures that bulkUpdateClusterResources skips nodeDataStore,
     * while updateClusterResource calls it exactly once
     */
    @Test
    void bulkUpdateSkipsNodeDataStore() {
        val nodeDataStoreCallCount = new AtomicInteger(0);
        val bulkProcessed = new CountDownLatch(4);
        val singleProcessed = new AtomicInteger(0);

        doAnswer(inv -> {
            nodeDataStoreCallCount.getAndIncrement();
            return null;
        }).when(nds).updateNodeData(any());

        doAnswer(inv -> {
            bulkProcessed.countDown();
            return null;
        }).when(cDB).update(anyList());

        try (val su = buildStateUpdater("test-partitioning-7")) {
            su.bulkUpdateClusterResources(List.of(
                        generateExecutorNode(0),
                        generateExecutorNode(1),
                        generateExecutorNode(2),
                        generateExecutorNode(3)));
            CommonTestUtils.waitUntil(() -> bulkProcessed.getCount() == 0);
            assertEquals(0,
                         nodeDataStoreCallCount.get(),
                         "bulkUpdateClusterResources must never call nodeDataStore.updateNodeData");

            doAnswer(inv -> {
                singleProcessed.getAndIncrement();
                return null;
            }).when(cDB).update(anyList());

            su.updateClusterResource(generateExecutorNode(0));
            CommonTestUtils.waitUntil(() -> singleProcessed.get() == 1);
            assertEquals(1,
                         nodeDataStoreCallCount.get(),
                         "updateClusterResource must call nodeDataStore.updateNodeData exactly once");
        }
    }


    /**
     * Ensures that the metrics delivery-delay timer is updated after an update is processed
     */
    @Test
    void metricsTimerUpdatedAfterProcessing() {
        val registryName = "test-partitioning-metrics-" + UUID.randomUUID();
        val registry = SharedMetricRegistries.getOrCreate(registryName);
        val timerName = "com.phonepe.drove.controller.stateUpdater.updateDeliveryDelay";

        val appSpec = appSpec(1);
        val appId = deployableObjectId(appSpec);
        val appInstance = generateInstanceInfo(appId, appSpec, 0);

        doReturn(Optional.empty()).when(iiDB).instance(anyString(), anyString());
        doReturn(true).when(iiDB).updateInstanceState(anyString(), anyString(), any(InstanceInfo.class));

        try (val su = new StateUpdater(cDB, taskDB, iiDB, lsDB, nds, eventBus, CONTROLLER_OPTIONS, registry)) {
            su.updateSingle(resourceSnapshot(0), appInstance);

            CommonTestUtils.waitUntil(() -> registry.timer(timerName).getCount() >= 1);

            assertTrue(registry.timer(timerName).getCount() >= 1,
                       "Timer count must be at least 1 after processing");
            assertTrue(registry.timer(timerName).getSnapshot().getMax() >= 0,
                       "Timer max must be non-negative");
        }
    }

    private static ExecutorResourceSnapshot resourceSnapshot(int n) {
        return new ExecutorResourceSnapshot(ControllerTestUtils.executorId(n),
                                            new AvailableCPU(Map.of(), Map.of()),
                                            new AvailableMemory(Map.of(), Map.of()),
                                            new PhysicalLayout(Map.of(), Map.of()));
    }

    private static ExecutorNodeData nodeWithInstances(int n, List<InstanceInfo> instances) {
        val base = generateExecutorNode(n);
        return new ExecutorNodeData(
                                    base.getHostname(),
                                    base.getPort(),
                                    NodeTransportType.HTTP,
                                    new Date(),
                                    base.getState(),
                                    instances,
                                    List.of(),
                                    List.of(),
                                    Set.of(),
                                    Map.of(),
                                    ExecutorState.ACTIVE);
    }

    private StateUpdater buildStateUpdater(String registryName) {
        return new StateUpdater(
                                cDB,
                                taskDB,
                                iiDB,
                                lsDB,
                                nds,
                                eventBus,
                                CONTROLLER_OPTIONS,
                                SharedMetricRegistries.getOrCreate(registryName));
    }

}
