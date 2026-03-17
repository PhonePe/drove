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

package com.phonepe.drove.controller.resourcemgmt;

import com.google.common.collect.Sets;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.application.requirements.GPURequirement;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.GPUInfo;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.resources.PhysicalLayout;
import com.phonepe.drove.models.info.resources.allocation.GPUAllocation;
import com.phonepe.drove.models.info.resources.available.AvailableCPU;
import com.phonepe.drove.models.info.resources.available.AvailableGPU;
import com.phonepe.drove.models.info.resources.available.AvailableMemory;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.phonepe.drove.controller.ControllerTestUtils.executorId;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class InMemoryClusterResourcesDBTest extends ControllerTestBase {

    private static class RecordingSelector implements ExecutorSelector {
        private final AtomicInteger invocations = new AtomicInteger();
        private final ExecutorSelector delegate;

        private RecordingSelector(ExecutorSelector delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<AllocatedExecutorNode> select(
                List<ExecutorHostInfo> nodes,
                java.util.function.Predicate<ExecutorHostInfo> nodeFilter,
                java.util.function.Function<ExecutorHostInfo, Optional<AllocatedExecutorNode>> allocator,
                java.util.function.Predicate<AllocatedExecutorNode> allocationFilter) {
            invocations.incrementAndGet();
            return delegate.select(nodes, nodeFilter, allocator, allocationFilter);
        }
    }

    @Test
    void testHostDetails() {
        val db = new InMemoryClusterResourcesDB();
        db.update(IntStream.rangeClosed(1, 100)
                .mapToObj(ControllerTestUtils::generateExecutorNode)
                .toList());
        {
            val snap = db.currentSnapshot(false);
            assertEquals(100, snap.size());
        }
        {
            IntStream.rangeClosed(1, 100)
                    .forEach(i -> assertEquals(executorId(i),
                            db.currentSnapshot(executorId(i))
                                    .map(ExecutorHostInfo::getExecutorId)
                                    .orElse(null)));
        }
        {
            db.remove(IntStream.rangeClosed(1, 100).mapToObj(ControllerTestUtils::executorId).toList());
            assertTrue(db.currentSnapshot(false).isEmpty());
            IntStream.rangeClosed(1, 100)
                    .forEach(i -> assertEquals(executorId(i),
                            db.lastKnownSnapshot(executorId(i))
                                    .map(ExecutorHostInfo::getExecutorId)
                                    .orElse(null)));
        }
        db.update(IntStream.rangeClosed(1, 100)
                .mapToObj(ControllerTestUtils::generateExecutorNode)
                .toList());
        {
            IntStream.rangeClosed(1, 100)
                    .forEach(i -> {
                        assertEquals(executorId(i),
                                db.currentSnapshot(executorId(i))
                                        .map(ExecutorHostInfo::getExecutorId)
                                        .orElse(null));
                        assertNull(db.lastKnownSnapshot(ControllerTestUtils.executorId(i)).orElse(null));
                    });
        }
    }

    @Test
    void testResources() {
        val db = new InMemoryClusterResourcesDB();
        assertTrue(db.currentSnapshot(false).isEmpty());
        db.update(new ExecutorResourceSnapshot("INVALID_EXECUTOR",
                new AvailableCPU(Map.of(0, Set.of(2, 3, 4)),
                        Map.of(1, Set.of(0, 1))),
                new AvailableMemory(
                        Map.of(0, 3 * 128 * (2L ^ 20)),
                        Map.of(0, 128 * (2L ^ 20))),
                new AvailableGPU(List.of(), List.of()),
                new PhysicalLayout(Map.of(0,
                        Set.of(0, 1, 2, 3, 4),
                        1,
                        Set.of(0, 1, 2, 3, 4)),
                        Map.of(0,
                                4 * 128 * (2L ^ 20),
                                1,
                                4 * 128 * (2L ^ 20)), Set.of())));
        assertTrue(db.currentSnapshot(false).isEmpty());
        val originalNodeData = ControllerTestUtils.generateExecutorNode(1);
        db.update(List.of(originalNodeData));
        val allocatedNode = db.selectNodes(List.of(new CPURequirement(2), new MemoryRequirement(128)),
                        EnumSet.of(ExecutorState.ACTIVE),
                        node -> true
                )
                .orElse(null);
        assertNotNull(allocatedNode);
        //Ensure nodes are allocated correctly
        {
            val nodeInfo = db.currentSnapshot(allocatedNode.getExecutorId()).orElse(null);
            assertNotNull(nodeInfo);
            //1. Free nodes have not been returned as allocated
            assertTrue(Sets.intersection(allocatedNode.getCpu().getCores().get(0),
                    nodeInfo.getNodes()
                            .get(0)
                            .getCores()
                            .entrySet()
                            .stream()
                            .filter(e -> e.getValue()
                                    .equals(ExecutorHostInfo.CoreState.FREE))
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toSet())).isEmpty());
            //2. Nodes marked as allocated are indeed accounted as allocated
            assertEquals(allocatedNode.getCpu().getCores().get(0),
                    nodeInfo.getNodes()
                            .get(0)
                            .getCores()
                            .entrySet()
                            .stream()
                            .filter(e -> e.getValue()
                                    .equals(ExecutorHostInfo.CoreState.ALLOCATED))
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toSet()));
        }

        //Receive update to mark allocated nodes as in use
        val originalResource = originalNodeData.getState();
        db.update(new ExecutorResourceSnapshot(allocatedNode.getExecutorId(),
                new AvailableCPU(Map.of(0,
                        Sets.difference(originalResource.getCpus()
                                        .getFreeCores()
                                        .get(0),
                                allocatedNode.getCpu()
                                        .getCores()
                                        .get(0))),
                        allocatedNode.getCpu().getCores()),
                new AvailableMemory(Map.of(0,
                        originalResource.getMemory()
                                .getFreeMemory()
                                .get(0) - allocatedNode.getMemory()
                                .getMemoryInMB()
                                .get(0)),
                        allocatedNode.getMemory().getMemoryInMB()),
                new AvailableGPU(List.of(), List.of()),
                originalResource.getLayout()
        ));
        //Ensure states have reflected properly
        {
            val nodeInfo = db.currentSnapshot(allocatedNode.getExecutorId()).orElse(null);
            assertNotNull(nodeInfo);
            assertEquals(allocatedNode.getCpu().getCores().get(0),
                    nodeInfo.getNodes()
                            .get(0)
                            .getCores()
                            .entrySet()
                            .stream()
                            .filter(e -> e.getValue()
                                    .equals(ExecutorHostInfo.CoreState.IN_USE))
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toSet()));
        }
        assertNull(db.selectNodes(List.of(new CPURequirement(4), new MemoryRequirement(128)),
                        EnumSet.of(ExecutorState.ACTIVE),
                        node -> true
                )
                .orElse(null));
        db.deselectNode(allocatedNode.getExecutorId(),
                allocatedNode.getCpu(),
                allocatedNode.getMemory(),
                new GPUAllocation(Set.of())); // Free up cores
        //Now it should be available
        assertNotNull(db.selectNodes(List.of(new CPURequirement(4), new MemoryRequirement(128)),
                        EnumSet.of(ExecutorState.ACTIVE),
                        node -> true
                )
                .orElse(null));
    }

    @Test
    void testBlacklisting() {
        val db = new InMemoryClusterResourcesDB();
        IntStream.rangeClosed(1, 10)
                .forEach(i -> db.markBlacklisted(executorId(i)));
        assertTrue(IntStream.rangeClosed(1, 10)
                        .allMatch(i -> db.isBlacklisted(executorId(i))),
                "Did not blacklist: " + IntStream.rangeClosed(1, 10)
                        .filter(i -> !db.isBlacklisted(executorId(i)))
                        .boxed()
                        .toList());
        db.update(IntStream.rangeClosed(1, 5)
                .mapToObj(ControllerTestUtils::generateExecutorNode)
                .toList());
        assertTrue(IntStream.rangeClosed(1, 10)
                        .allMatch(i -> db.isBlacklisted(executorId(i))),
                "Did not blacklist: " + IntStream.rangeClosed(1, 10)
                        .filter(i -> !db.isBlacklisted(executorId(i)))
                        .boxed()
                        .toList()); //This will prioritise the info in local map
        IntStream.rangeClosed(1, 10)
                .forEach(i -> db.unmarkBlacklisted(executorId(i)));
        assertTrue(IntStream.rangeClosed(1, 10)
                .noneMatch(i -> db.isBlacklisted(executorId(i))));
        db.update(IntStream.rangeClosed(1, 10)
                .mapToObj(index -> ControllerTestUtils.generateExecutorNode(index, Set.of(), true))
                .toList());
        assertTrue(IntStream.rangeClosed(1, 10)
                .allMatch(i -> db.isBlacklisted(executorId(i)))); //This will use node level data

    }

    @Test
    void testExecutorState() {
        val db = new InMemoryClusterResourcesDB();
        val originalNodeData = ControllerTestUtils.generateExecutorNode(1);
        val executorId = originalNodeData.getState().getExecutorId();

        db.update(List.of(originalNodeData));
        { // Allocate node and assert that it is active
            val allocatedNode = db.selectNodes(List.of(new CPURequirement(2), new MemoryRequirement(128)),
                            EnumSet.of(ExecutorState.ACTIVE),
                            node -> true
                    )
                    .orElse(null);
            assertNotNull(allocatedNode);
            assertTrue(db.isActive(allocatedNode.getExecutorId()));
            assertFalse(db.isBlacklisted(allocatedNode.getExecutorId()));
            db.deselectNode(executorId, allocatedNode.getCpu(), allocatedNode.getMemory(), new GPUAllocation(Set.of()));
        }
        {
            // Now blacklist node and assert that node is not allocated
            db.markBlacklisted(executorId);
            assertFalse(db.isActive(executorId));
            assertTrue(db.isBlacklisted(executorId));
            assertNull(db.selectNodes(List.of(new CPURequirement(2), new MemoryRequirement(128)),
                            EnumSet.of(ExecutorState.ACTIVE),
                            node -> true
                    )
                    .orElse(null));

            // Now select node with blacklisted state allowed
            final var allocatedNode = db.selectNodes(List.of(new CPURequirement(2), new MemoryRequirement(128)),
                            EnumSet.of(ExecutorState.BLACKLISTED),
                            node -> true
                    )
                    .orElse(null);
            assertNotNull(allocatedNode);
            db.deselectNode(executorId, allocatedNode.getCpu(), allocatedNode.getMemory(), new GPUAllocation(Set.of()));
        }
        {
            // Now unblacklist node and assert that node is allocated
            db.unmarkBlacklisted(executorId);
            assertTrue(db.isActive(executorId));
            assertFalse(db.isBlacklisted(executorId));
            assertNotNull(db.selectNodes(List.of(new CPURequirement(2), new MemoryRequirement(128)),
                            EnumSet.of(ExecutorState.ACTIVE),
                            node -> true
                    )
                    .orElse(null));
        }
        {
            // Now mark node as blacklisted by updating state
            db.update(List.of(ExecutorNodeData.from(
                    originalNodeData,
                    originalNodeData.getState(),
                    originalNodeData.getInstances(),
                    originalNodeData.getTasks(),
                    originalNodeData.getServiceInstances(),
                    originalNodeData.getTags(),
                    ExecutorState.BLACKLISTED,
                    originalNodeData.getMetadata()
            )));
            assertFalse(db.isActive(executorId));
            assertTrue(db.isBlacklisted(executorId));
            assertNull(db.selectNodes(List.of(new CPURequirement(2), new MemoryRequirement(128)),
                            EnumSet.of(ExecutorState.ACTIVE),
                            node -> true
                    )
                    .orElse(null));

            final var allocatedNode = db.selectNodes(List.of(new CPURequirement(2), new MemoryRequirement(128)),
                            EnumSet.of(ExecutorState.BLACKLISTED),
                            node -> true
                    )
                    .orElse(null);
            assertNotNull(allocatedNode);
            db.deselectNode(executorId, allocatedNode.getCpu(), allocatedNode.getMemory(), new GPUAllocation(Set.of()));
        }
        {
            // Now mark node as active by updating state
            db.update(List.of(ExecutorNodeData.from(
                    originalNodeData,
                    originalNodeData.getState(),
                    originalNodeData.getInstances(),
                    originalNodeData.getTasks(),
                    originalNodeData.getServiceInstances(),
                    originalNodeData.getTags(),
                    ExecutorState.ACTIVE,
                    originalNodeData.getMetadata()
            )));
            assertTrue(db.isActive(executorId));
            assertFalse(db.isBlacklisted(executorId));
            assertNotNull(db.selectNodes(List.of(new CPURequirement(2), new MemoryRequirement(128)),
                            EnumSet.of(ExecutorState.ACTIVE),
                            node -> true
                    )
                    .orElse(null));
        }
    }

    @Test
    void testExecutorStateWithGPU() {
        val db = new InMemoryClusterResourcesDB();
        val originalNodeData = ControllerTestUtils.generateExecutorNode(1);
        val executorId = originalNodeData.getState().getExecutorId();

        db.update(List.of(originalNodeData));
        { // Allocate node and assert that it is active
            val allocatedNode = db.selectNodes(List.of(new CPURequirement(2), new MemoryRequirement(128)),
                            EnumSet.of(ExecutorState.ACTIVE),
                            node -> true
                    )
                    .orElse(null);
            assertNotNull(allocatedNode);
            assertTrue(db.isActive(allocatedNode.getExecutorId()));
            assertFalse(db.isBlacklisted(allocatedNode.getExecutorId()));
            db.deselectNode(executorId, allocatedNode.getCpu(), allocatedNode.getMemory(), new GPUAllocation(Set.of()));
        }
    }


    @Test
    void testUpdatedSignal() {
        val db = new InMemoryClusterResourcesDB();
        val changesReceived = new AtomicInteger(0);
        final var testHandler = "TEST_HANDLER";
        db.onTopologyChange().connect(testHandler, changes -> {
            assertEquals(10, changes.addedExecutors().size());
            assertEquals(0, changes.removedExecutors().size());
            assertEquals(10, changes.currentLive().size());
            changesReceived.incrementAndGet();
        });
        val originalNodes = IntStream.rangeClosed(1, 10)
                .mapToObj(ControllerTestUtils::generateExecutorNode)
                .toList();
        val currValue = new AtomicInteger(changesReceived.get());
        db.update(originalNodes);

        // Ensure the assertions are triggered
        CommonTestUtils.waitUntil(() -> changesReceived.get() > currValue.get());
        db.onTopologyChange().disconnect(testHandler);

        val toBlacklist = originalNodes.subList(0, 5)
                .stream()
                .map(node -> node.getState().getExecutorId())
                .collect(Collectors.toSet());
        val toRemain = originalNodes.subList(5, 10)
                .stream()
                .map(node -> node.getState().getExecutorId())
                .collect(Collectors.toSet());

        // Now let us blacklist some nodes and ensure the logic is correct for livenodes
        currValue.set(changesReceived.get());
        db.onTopologyChange().connect(testHandler, changes -> {
            changesReceived.incrementAndGet();
            assertTrue(toBlacklist.containsAll(changes.removedExecutors()));
            assertEquals(0, changes.addedExecutors().size());
        });
        toBlacklist.forEach(db::markBlacklisted);
        // Ensure the assertions are triggered
        CommonTestUtils.waitUntil(() -> changesReceived.get() == (currValue.get() + 5));
        db.onTopologyChange().disconnect(testHandler);
        assertEquals(toRemain, db.currentSnapshot(true)
                .stream()
                .map(ExecutorHostInfo::getExecutorId)
                .collect(Collectors.toUnmodifiableSet()));


        // now we bring the nodes back up and ensure the signal is raised
        currValue.set(changesReceived.get());
        db.onTopologyChange().connect(testHandler, changes -> {
            changesReceived.incrementAndGet();
            assertTrue(toBlacklist.containsAll(changes.addedExecutors()));
            assertEquals(0, changes.removedExecutors().size());
        });
        toBlacklist.forEach(db::unmarkBlacklisted);
        // Ensure the assertions are triggered
        CommonTestUtils.waitUntil(() -> changesReceived.get() == (currValue.get() + 5));
        db.onTopologyChange().disconnect(testHandler);
        assertEquals(Sets.union(toBlacklist, toRemain), db.currentSnapshot(true)
                .stream()
                .map(ExecutorHostInfo::getExecutorId)
                .collect(Collectors.toUnmodifiableSet()));

        // Finally we remove nodes and ensure signal is raised
        currValue.set(changesReceived.get());
        db.onTopologyChange().connect(testHandler, changes -> {
            changesReceived.addAndGet(changes.removedExecutors().size());
            assertTrue(toBlacklist.containsAll(changes.removedExecutors()));
            assertEquals(0, changes.addedExecutors().size());
        });
        db.remove(toBlacklist);
        // Ensure the assertions are triggered
        CommonTestUtils.waitUntil(() -> changesReceived.get() == (currValue.get() + 5));
        db.onTopologyChange().disconnect(testHandler);
        assertEquals(toRemain, db.currentSnapshot(true)
                .stream()
                .map(ExecutorHostInfo::getExecutorId)
                .collect(Collectors.toUnmodifiableSet()));
    }

    @Test
    void testCpuMemRequestsUseDefaultSelector() {
        val defaultSelector = new RecordingSelector(new FirstFitExecutorSelector());
        val gpuSelector = new RecordingSelector(new GpuAwareExecutorSelector());
        val db = new InMemoryClusterResourcesDB(new GpuAwareNumaExecutorResourceAllocator(),
                defaultSelector,
                gpuSelector);

        db.update(List.of(ControllerTestUtils.generateExecutorNode(1)));

        val result = db.selectNodes(
                List.of(new CPURequirement(1), new MemoryRequirement(128)),
                EnumSet.of(ExecutorState.ACTIVE),
                node -> true,
                false);

        assertTrue(result.isPresent());
        assertEquals(1, defaultSelector.invocations.get());
        assertEquals(0, gpuSelector.invocations.get());
    }

    @Test
    void testGpuRequestsUseGpuAwareSelector() {
        val defaultSelector = new RecordingSelector(new FirstFitExecutorSelector());
        val gpuSelector = new RecordingSelector(new GpuAwareExecutorSelector());
        val db = new InMemoryClusterResourcesDB(new GpuAwareNumaExecutorResourceAllocator(),
                defaultSelector,
                gpuSelector);

        db.update(List.of(buildExecutorNodeData(
                "executor-1",
                Map.of(0, Set.of(0, 1, 2, 3)),
                Map.of(0, 2048L),
                Map.of("GPU-0", 0))));

        val result = db.selectNodes(
                List.of(new CPURequirement(1), new MemoryRequirement(128), new GPURequirement(1)),
                EnumSet.of(ExecutorState.ACTIVE),
                node -> true,
                false);

        assertTrue(result.isPresent());
        assertEquals(0, defaultSelector.invocations.get());
        assertEquals(1, gpuSelector.invocations.get());
    }

    private ExecutorNodeData buildExecutorNodeData(
            String executorId,
            Map<Integer, Set<Integer>> freeCoresPerNuma,
            Map<Integer, Long> freeMemoryPerNuma,
            Map<String, Integer> gpuNumaAffinities) {
        val freeGpus = new java.util.ArrayList<>(gpuNumaAffinities.keySet());
        val usedMemory = freeMemoryPerNuma.keySet().stream()
                .collect(Collectors.toUnmodifiableMap(k -> k, k -> 0L));
        val physicalGpus = gpuNumaAffinities.entrySet().stream()
                .map(entry -> new GPUInfo(entry.getKey(), "NVIDIA", "H100", entry.getValue(), true))
                .collect(Collectors.toUnmodifiableSet());

        val snapshot = new ExecutorResourceSnapshot(
                executorId,
                new AvailableCPU(freeCoresPerNuma, Map.of()),
                new AvailableMemory(freeMemoryPerNuma, usedMemory),
                new AvailableGPU(freeGpus, List.of()),
                new PhysicalLayout(freeCoresPerNuma, freeMemoryPerNuma, physicalGpus)
        );

        return new ExecutorNodeData(
                "host-" + executorId,
                8080,
                NodeTransportType.HTTP,
                new Date(),
                snapshot,
                List.of(),
                List.of(),
                List.of(),
                Set.of(),
                Map.of(),
                ExecutorState.ACTIVE
        );
    }

    @Test
    void testGpuSelectionPrefersExecutorWithNumaLocalGpu() {
        val db = new InMemoryClusterResourcesDB();

        // Executor-1: NUMA 0 has CPUs + memory, NUMA 1 has all GPUs (cross-NUMA scenario)
        val executor1NodeData = buildExecutorNodeData(
                "executor-1",
                Map.of(0, Set.of(0, 1, 2, 3)),  // CPUs only on NUMA 0
                Map.of(0, 2048L, 1, 2048L),      // Memory on both
                Map.of("GPU-0", 1, "GPU-1", 1)   // All GPUs on NUMA 1
        );

        // Executor-2: NUMA 0 has both CPUs and local GPUs
        val executor2NodeData = buildExecutorNodeData(
                "executor-2",
                Map.of(0, Set.of(0, 1, 2, 3)),   // CPUs on NUMA 0
                Map.of(0, 2048L),                 // Memory on NUMA 0 only
                Map.of("GPU-0", 0, "GPU-1", 0)   // GPUs local to NUMA 0
        );

        db.update(List.of(executor1NodeData, executor2NodeData));

        // Request: 1 CPU, 128MB memory, 1 GPU
        val result = db.selectNodes(
                List.of(new CPURequirement(1), new MemoryRequirement(128), new GPURequirement(1)),
                EnumSet.of(ExecutorState.ACTIVE),
                node -> true,
                false
        );

        assertTrue(result.isPresent(), "Should successfully allocate on at least one executor");
        val allocated = result.get();
        assertEquals("executor-2", allocated.getExecutorId(),
                "Executor with NUMA-local GPU allocation should be preferred over cross-NUMA fallback");
    }

}
