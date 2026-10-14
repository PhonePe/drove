/*
 * Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.drove.controller.resourcemgmt;

import com.google.common.collect.Sets;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.resourcemgmt.selector.*;
import com.phonepe.drove.controller.resourcemgmt.selector.scored.MaxGpuNumaLocalAllocation;
import com.phonepe.drove.controller.resourcemgmt.selector.scored.ScoredExecutorSelector;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.GpuType;
import com.phonepe.drove.models.application.requirements.GpuVendor;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.application.requirements.GPURequirement;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.GPUInfo;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.resources.PhysicalLayout;
import com.phonepe.drove.models.info.resources.allocation.AllocatedExecutorNode;
import com.phonepe.drove.models.info.resources.allocation.GPUAllocation;
import com.phonepe.drove.models.info.resources.available.AvailableCPU;
import com.phonepe.drove.models.info.resources.available.AvailableGPU;
import com.phonepe.drove.models.info.resources.available.AvailableMemory;
import lombok.val;

import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
                                                      List<ResourceRequirement> requirements,
                                                      java.util.function.Predicate<AllocatedExecutorNode> allocationFilter) {
            invocations.incrementAndGet();
            return delegate.select(nodes, nodeFilter, requirements, allocationFilter);
        }
    }

    @Test
    void testHostDetails() {
        val db = getDb();
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
        val db = getDb();
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
                                                                         4 * 128 * (2L ^ 20)),
                                                                  Map.of(),
                                                                  PhysicalLayout.DEFAULT_CPU_SCALING_FACTOR,
                                                                  PhysicalLayout.DEFAULT_MEMORY_SCALING_FACTOR)));
        assertTrue(db.currentSnapshot(false).isEmpty());
        val originalNodeData = ControllerTestUtils.generateExecutorNode(1);
        db.update(List.of(originalNodeData));
        val allocatedNode = db.selectNodes(List.of(new CPURequirement(2), new MemoryRequirement(128)),
                                           EnumSet.of(ExecutorState.ACTIVE),
                                           ctx -> true,
                                           true)
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
                                  ctx -> true,
                                  true)
                .orElse(null));
        db.deselectNode(allocatedNode.getExecutorId(),
                        allocatedNode.getCpu(),
                        allocatedNode.getMemory(),
                        new GPUAllocation(Set.of(), null)); // Free up cores
        //Now it should be available
        assertNotNull(db.selectNodes(List.of(new CPURequirement(4), new MemoryRequirement(128)),
                                     EnumSet.of(ExecutorState.ACTIVE),
                                     ctx -> true,
                                     true)
                .orElse(null));
    }

    @Test
    void testBlacklisting() {
        val db = getDb();
        // Update executor state to blacklisted
        db.update(IntStream.rangeClosed(1, 10)
                .mapToObj(index -> ControllerTestUtils.generateExecutorNode(index, Set.of(), true))
                .toList());
        assertTrue(IntStream.rangeClosed(1, 10)
                .allMatch(i -> db.isBlacklisted(executorId(i))),
                   "Did not blacklist: " + IntStream.rangeClosed(1, 10)
                           .filter(i -> !db.isBlacklisted(executorId(i)))
                           .boxed()
                           .toList());
        db.update(IntStream.rangeClosed(1, 10)
                .mapToObj(index -> ControllerTestUtils.generateExecutorNode(index, Set.of(), false))
                .toList());
        assertTrue(IntStream.rangeClosed(1, 10)
                .allMatch(i -> !db.isBlacklisted(executorId(i)))); //This will use node level data

    }

    @Test
    void testExecutorState() {
        val db = getDb();
        val originalNodeData = ControllerTestUtils.generateExecutorNode(1);
        val executorId = originalNodeData.getState().getExecutorId();

        db.update(List.of(originalNodeData));
        { // Allocate node and assert that it is active
            val allocatedNode = db.selectNodes(List.of(new CPURequirement(2), new MemoryRequirement(128)),
                                               EnumSet.of(ExecutorState.ACTIVE),
                                               ctx -> true,
                                               true
            )
                    .orElse(null);
            assertNotNull(allocatedNode);
            assertTrue(db.isActive(allocatedNode.getExecutorId()));
            assertFalse(db.isBlacklisted(allocatedNode.getExecutorId()));
            db.deselectNode(executorId, allocatedNode.getCpu(), allocatedNode.getMemory(), allocatedNode.getGpu());
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
                                      ctx -> true,
                                      true
            )
                    .orElse(null));

            final var allocatedNode = db.selectNodes(List.of(new CPURequirement(2), new MemoryRequirement(128)),
                                                     EnumSet.of(ExecutorState.BLACKLISTED),
                                                     ctx -> true,
                                                     true
            )
                    .orElse(null);
            assertNotNull(allocatedNode);
            db.deselectNode(executorId, allocatedNode.getCpu(), allocatedNode.getMemory(), allocatedNode.getGpu());
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
                                         ctx -> true,
                                         true
            )
                    .orElse(null));
        }
    }

    @Test
    void testExecutorStateWithGPU() {
        val db = getDb();
        val originalNodeData = ControllerTestUtils.generateExecutorNode(1);
        val executorId = originalNodeData.getState().getExecutorId();

        db.update(List.of(originalNodeData));
        { // Allocate node and assert that it is active
            val allocatedNode = db.selectNodes(List.of(new CPURequirement(2), new MemoryRequirement(128)),
                                               EnumSet.of(ExecutorState.ACTIVE),
                                               node -> true,
                                               true)
                    .orElse(null);
            assertNotNull(allocatedNode);
            assertTrue(db.isActive(allocatedNode.getExecutorId()));
            assertFalse(db.isBlacklisted(allocatedNode.getExecutorId()));
            db.deselectNode(executorId,
                            allocatedNode.getCpu(),
                            allocatedNode.getMemory(),
                            new GPUAllocation(Set.of(), null));
        }
    }

    @Test
    void testUpdatedSignal() {
        val db = getDb();
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
                .toList();
        val blackListedNodeIds = toBlacklist.stream()
                .map(executor -> executor.getState().getExecutorId())
                .collect(Collectors.toUnmodifiableSet());
        val toRemain = originalNodes.subList(5, 10)
                .stream()
                .map(node -> node.getState().getExecutorId())
                .collect(Collectors.toUnmodifiableSet());

        // Now let us blacklist some nodes and ensure the logic is correct for livenodes
        currValue.set(changesReceived.get());
        db.onTopologyChange().connect(testHandler, changes -> {
            changesReceived.incrementAndGet();
            assertTrue(blackListedNodeIds.containsAll(changes.removedExecutors()));
            assertEquals(0, changes.addedExecutors().size());
        });
        toBlacklist.forEach(executor -> db.update(List.of(ExecutorNodeData.from(executor,
                                                                                executor.getState(),
                                                                                executor.getInstances(),
                                                                                executor.getTasks(),
                                                                                executor.getServiceInstances(),
                                                                                executor.getTags(),
                                                                                ExecutorState.BLACKLISTED,
                                                                                executor.getMetadata()
        ))));
        // Ensure the assertions are triggered
        CommonTestUtils.waitUntil(() -> changesReceived.get() == (currValue.get() + 5));
        db.onTopologyChange().disconnect(testHandler);
        assertEquals(toRemain,
                     db.currentSnapshot(true)
                             .stream()
                             .map(ExecutorHostInfo::getExecutorId)
                             .collect(Collectors.toUnmodifiableSet()));

        // now we bring the nodes back up and ensure the signal is raised
        currValue.set(changesReceived.get());
        db.onTopologyChange().connect(testHandler, changes -> {
            changesReceived.incrementAndGet();
            assertTrue(blackListedNodeIds.containsAll(changes.addedExecutors()));
            assertEquals(0, changes.removedExecutors().size());
        });
        toBlacklist.forEach(executor -> db.update(List.of(ExecutorNodeData.from(executor,
                                                                                executor.getState(),
                                                                                executor.getInstances(),
                                                                                executor.getTasks(),
                                                                                executor.getServiceInstances(),
                                                                                executor.getTags(),
                                                                                ExecutorState.ACTIVE,
                                                                                executor.getMetadata()
        ))));
        // Ensure the assertions are triggered
        CommonTestUtils.waitUntil(() -> changesReceived.get() == (currValue.get() + 5));
        db.onTopologyChange().disconnect(testHandler);
        assertEquals(Sets.union(blackListedNodeIds, toRemain),
                     db.currentSnapshot(true)
                             .stream()
                             .map(ExecutorHostInfo::getExecutorId)
                             .collect(Collectors.toUnmodifiableSet()));

        // Finally we remove nodes and ensure signal is raised
        currValue.set(changesReceived.get());
        db.onTopologyChange().connect(testHandler, changes -> {
            changesReceived.addAndGet(changes.removedExecutors().size());
            assertTrue(blackListedNodeIds.containsAll(changes.removedExecutors()));
            assertEquals(0, changes.addedExecutors().size());
        });
        db.remove(blackListedNodeIds);
        // Ensure the assertions are triggered
        CommonTestUtils.waitUntil(() -> changesReceived.get() == (currValue.get() + 5));
        db.onTopologyChange().disconnect(testHandler);
        assertEquals(toRemain,
                     db.currentSnapshot(true)
                             .stream()
                             .map(ExecutorHostInfo::getExecutorId)
                             .collect(Collectors.toUnmodifiableSet()));
    }

    @Test
    void testCpuMemRequestsUseDefaultSelector() {
        val allocator = ControllerTestUtils.gpuAwareAllocator(0, 0);
        val recordingDefaultSelector = new RecordingSelector(new FirstFitExecutorSelector(allocator));
        val recordingGpuSelector = new RecordingSelector(new ScoredExecutorSelector(allocator,
                                                                                    new MaxGpuNumaLocalAllocation()));
        val compositeSelector = new CompositeExecutorSelector(recordingDefaultSelector, recordingGpuSelector);
        val db = new InMemoryClusterResourcesDB(compositeSelector);

        db.update(List.of(ControllerTestUtils.generateExecutorNode(1)));

        val result = db.selectNodes(
                                    List.of(new CPURequirement(1), new MemoryRequirement(128)),
                                    EnumSet.of(ExecutorState.ACTIVE),
                                    node -> true,
                                    false);

        assertTrue(result.isPresent());
        assertEquals(1, recordingDefaultSelector.invocations.get());
        assertEquals(0, recordingGpuSelector.invocations.get());
    }

    @Test
    void testGpuRequestsUseGpuAwareSelector() {
        val allocator = ControllerTestUtils.gpuAwareAllocator(0, 0);
        val recordingDefaultSelector = new RecordingSelector(new FirstFitExecutorSelector(allocator));
        val recordingGpuSelector = new RecordingSelector(new ScoredExecutorSelector(allocator,
                                                                                    new MaxGpuNumaLocalAllocation()));
        val compositeSelector = new CompositeExecutorSelector(recordingDefaultSelector, recordingGpuSelector);
        val db = new InMemoryClusterResourcesDB(compositeSelector);

        db.update(List.of(buildExecutorNodeData(
                                                "executor-1",
                                                Map.of(0, Set.of(0, 1, 2, 3)),
                                                Map.of(0, 2048L),
                                                Map.of("GPU-0", 0))));

        val result = db.selectNodes(
                                    List.of(new CPURequirement(1),
                                            new MemoryRequirement(128),
                                            new GPURequirement(1, new GpuType(GpuVendor.NVIDIA, "h100"))),
                                    EnumSet.of(ExecutorState.ACTIVE),
                                    node -> true,
                                    false);

        assertTrue(result.isPresent());
        assertEquals(0, recordingDefaultSelector.invocations.get());
        assertEquals(1, recordingGpuSelector.invocations.get());
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
                .collect(Collectors.toUnmodifiableMap(
                                                      Map.Entry::getKey,
                                                      entry -> new GPUInfo(entry.getKey(),
                                                                           GpuVendor.NVIDIA,
                                                                           "H100",
                                                                           entry.getValue())));

        val snapshot = new ExecutorResourceSnapshot(
                                                    executorId,
                                                    new AvailableCPU(freeCoresPerNuma, Map.of()),
                                                    new AvailableMemory(freeMemoryPerNuma, usedMemory),
                                                    new AvailableGPU(freeGpus, List.of()),
                                                    new PhysicalLayout(freeCoresPerNuma,
                                                                       freeMemoryPerNuma,
                                                                       physicalGpus,
                                                                       PhysicalLayout.DEFAULT_CPU_SCALING_FACTOR,
                                                                       PhysicalLayout.DEFAULT_MEMORY_SCALING_FACTOR)
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
        val db = getDb();

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
                                    List.of(new CPURequirement(1),
                                            new MemoryRequirement(128),
                                            new GPURequirement(1, new GpuType(GpuVendor.NVIDIA, "h100"))),
                                    EnumSet.of(ExecutorState.ACTIVE),
                                    node -> true,
                                    false
        );

        assertTrue(result.isPresent(), "Should successfully allocate on at least one executor");
        val allocated = result.get();
        assertEquals("executor-2",
                     allocated.getExecutorId(),
                     "Executor with NUMA-local GPU allocation should be preferred over cross-NUMA fallback");
    }

    @Test
    void testFilterContextDataPropagation() {
        val db = getDb();
        val originalNodeData = ControllerTestUtils.generateExecutorNode(1);
        db.update(List.of(originalNodeData));

        val filterContext = new AtomicReference<ClusterResourcesDB.FilterContext>();
        val allocatedNode = db.selectNodes(List.of(new CPURequirement(2), new MemoryRequirement(128)),
                                           EnumSet.of(ExecutorState.ACTIVE),
                                           ctx -> {
                                               filterContext.set(ctx);
                                               return true;
                                           },
                                           true)
                .orElse(null);

        assertNotNull(allocatedNode);
        assertNotNull(filterContext);
        assertEquals(1, filterContext.get().getLiveExecutors().size());
        assertEquals(executorId(1), filterContext.get().getLiveExecutors().get(0).getExecutorId());
        assertEquals(originalNodeData, filterContext.get().getAllocatedExecutorNodeData());
        assertEquals(allocatedNode, filterContext.get().getAllocatedExecutorNode());
    }

    @Test
    void testFilterContextEmptyWhenNodeDisappears() {
        val db = getDb();
        db.update(List.of(ControllerTestUtils.generateExecutorNode(1)));
        assertNotNull(db.selectNodes(List.of(new CPURequirement(2), new MemoryRequirement(128)),
                                     EnumSet.of(ExecutorState.ACTIVE),
                                     ctx -> true,
                                     true)
                .orElse(null));
        db.remove(List.of(executorId(1)));
        assertNull(db.selectNodes(List.of(new CPURequirement(2), new MemoryRequirement(128)),
                                  EnumSet.of(ExecutorState.ACTIVE),
                                  ctx -> true,
                                  true)
                .orElse(null));
    }

    @Test
    void testBulkExecutorSnapshotLookup() {
        val db = getDb();
        db.update(IntStream.rangeClosed(1, 5)
                .mapToObj(ControllerTestUtils::generateExecutorNode)
                .toList());

        val allIds = IntStream.rangeClosed(1, 5)
                .mapToObj(ControllerTestUtils::executorId)
                .collect(Collectors.toUnmodifiableSet());

        val ghostIds = Set.of("ghost-1", "ghost-2");

        // All existing IDs → full map with correct ExecutorHostInfo per entry
        val fullResult = db.currentSnapshot(allIds);
        assertEquals(5, fullResult.size());
        allIds.forEach(id -> assertEquals(id, fullResult.get(id).getExecutorId()));

        // Empty input set → empty map regardless of DB contents
        assertTrue(db.currentSnapshot(Set.of()).isEmpty());

        // All non-existent IDs → empty map, no Optional.empty() noise
        assertTrue(db.currentSnapshot(ghostIds).isEmpty());

        // Partial: mix of known (1-3) and unknown IDs → only known IDs in result
        val existingIds = IntStream.rangeClosed(1, 3)
                .mapToObj(ControllerTestUtils::executorId)
                .collect(Collectors.toUnmodifiableSet());

        val partialResult = db.currentSnapshot(Sets.union(existingIds, ghostIds));
        assertEquals(3, partialResult.size());
        existingIds.forEach(id -> assertTrue(partialResult.containsKey(id)));
        ghostIds.forEach(id -> assertFalse(partialResult.containsKey(id)));
    }

    @Nonnull
    @Test
    void testDeepCopyDoesNotShareMutableState() {
        val db = getDb();
        db.update(List.of(ControllerTestUtils.generateExecutorNode(1)));
        val original = db.currentSnapshot(executorId(1)).orElseThrow();
        val copy = original.shallowCopy();
        assertSame(original.getNodeData(), copy.getNodeData());
        assertEquals(original.getNodes().get(0).getCores(), copy.getNodes().get(0).getCores());

        copy.getNodes().get(0).getCores().put(0, ExecutorHostInfo.CoreState.ALLOCATED);
        copy.getNodes().get(0).getMemory().setAvailable(0L);
        copy.getGpuInfo().getGpus().put("GPU-X", ExecutorHostInfo.CoreState.ALLOCATED);

        assertEquals(ExecutorHostInfo.CoreState.FREE, original.getNodes().get(0).getCores().get(0));
        assertNotEquals(0L, original.getNodes().get(0).getMemory().getAvailable());
        assertFalse(original.getGpuInfo().getGpus().containsKey("GPU-X"));
    }

    @Test
    void testCopyIsIsolatedFromOriginal() {
        val db = getDb();
        db.update(List.of(ControllerTestUtils.generateExecutorNode(1)));
        val copy = db.copy();
        //5 cores on the executor; the copy can use all of them
        IntStream.rangeClosed(1, 5)
                .forEach(i -> assertTrue(copy.selectNodes(List.of(new CPURequirement(1), new MemoryRequirement(128)),
                                                          EnumSet.of(ExecutorState.ACTIVE),
                                                          ctx -> true,
                                                          false)
                        .isPresent()));
        assertTrue(copy.selectNodes(List.of(new CPURequirement(1), new MemoryRequirement(128)),
                                    EnumSet.of(ExecutorState.ACTIVE),
                                    ctx -> true,
                                    false)
                .isEmpty());
        //The original is not changed
        val originalCores = db.currentSnapshot(executorId(1)).orElseThrow().getNodes().get(0).getCores();
        assertTrue(originalCores.values().stream().allMatch(ExecutorHostInfo.CoreState.FREE::equals));
        assertTrue(db.selectNodes(List.of(new CPURequirement(5), new MemoryRequirement(128)),
                                  EnumSet.of(ExecutorState.ACTIVE),
                                  ctx -> true,
                                  false)
                .isPresent());
    }

    @Test
    void testSingleSelectionHasNullInstanceId() {
        val db = getDb();
        db.update(List.of(ControllerTestUtils.generateExecutorNode(1)));
        val instanceIds = new ArrayList<String>();
        assertTrue(db.selectNodes(List.of(new CPURequirement(1), new MemoryRequirement(128)),
                                  EnumSet.of(ExecutorState.ACTIVE),
                                  ctx -> {
                                      instanceIds.add(ctx.getInstanceId());
                                      return true;
                                  },
                                  false)
                .isPresent());
        assertFalse(instanceIds.isEmpty());
        assertTrue(instanceIds.stream().allMatch(Objects::isNull));
    }

    private static InMemoryClusterResourcesDB getDb() {
        return ControllerTestUtils.clusterResourcesDB();
    }

}
