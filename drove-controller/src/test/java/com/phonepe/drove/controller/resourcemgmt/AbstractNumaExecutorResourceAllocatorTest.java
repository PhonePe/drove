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

import com.phonepe.drove.controller.resourcemgmt.allocator.AbstractNumaExecutorResourceAllocator;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AbstractNumaResourceAllocator verifying that CPU allocation,
 * memory allocation, and node assembly work correctly regardless of the GPU strategy
 * used by a concrete subclass.
 */
class AbstractNumaExecutorResourceAllocatorTest {

    /**
     * Minimal concrete stub: always picks the first candidate NUMA node and
     * delegates entirely to the base-class helpers. Contains no GPU logic.
     */
    private static class FirstCandidateAllocatorExecutor extends AbstractNumaExecutorResourceAllocator {
        @Override
        public Optional<AllocatedExecutorNode> allocate(
                ExecutorHostInfo hostInfo,
                long requiredCpus,
                long requiredMemory,
                long requiredGpus) {
            val candidateNumaNodeIds = candidateNumaNodeIds(hostInfo, requiredCpus, requiredMemory);
            if (candidateNumaNodeIds.isEmpty()) {
                return Optional.empty();
            }
            val numaNodeId = candidateNumaNodeIds.get(0);
            return Optional.of(buildAllocatedNode(
                    hostInfo, numaNodeId, requiredCpus, requiredMemory,
                    new GPUAllocation(Set.of(), 0)));
        }
    }

    private final FirstCandidateAllocatorExecutor allocator = new FirstCandidateAllocatorExecutor();

    // -------------------------------------------------------------------------
    // CPU allocation
    // -------------------------------------------------------------------------

    @Test
    void testAllocateCPUsSelectsExactlyRequiredCores() {
        val hostInfo = buildHostInfo(Map.of(0, Set.of(0, 1, 2, 3)), Map.of(0, 2048L));

        val result = allocator.allocate(hostInfo, 2, 128, 0);

        assertTrue(result.isPresent());
        val coreSets = result.get().getCpu().getCores();
        assertEquals(1, coreSets.size(), "Allocation should span exactly one NUMA node");
        assertEquals(2, coreSets.get(0).size(), "Exactly 2 cores should be allocated");
    }

    @Test
    void testAllocateCPUsSelectsOnlyFreeCores() {
        // NUMA 0: cores 0 and 1 are IN_USE, cores 2 and 3 are FREE
        val numaNodeInfo = new ExecutorHostInfo.NumaNodeInfo();
        numaNodeInfo.getCores().put(0, ExecutorHostInfo.CoreState.IN_USE);
        numaNodeInfo.getCores().put(1, ExecutorHostInfo.CoreState.IN_USE);
        numaNodeInfo.getCores().put(2, ExecutorHostInfo.CoreState.FREE);
        numaNodeInfo.getCores().put(3, ExecutorHostInfo.CoreState.FREE);
        numaNodeInfo.getMemory().setAvailable(2048L);

        val gpuInfo = new ExecutorHostInfo.HostGPUInfo();
        val hostInfo = new ExecutorHostInfo("EX-1", buildNodeData(), Map.of(0, numaNodeInfo), gpuInfo);

        val result = allocator.allocate(hostInfo, 1, 128, 0);

        assertTrue(result.isPresent());
        val allocatedCores = result.get().getCpu().getCores().get(0);
        assertEquals(1, allocatedCores.size());
        assertTrue(allocatedCores.stream().allMatch(c -> c == 2 || c == 3),
                   "Only free cores (2 or 3) should be allocated, not in-use cores 0/1");
    }

    @Test
    void testAllocateCPUsRespectsRequestedCount() {
        val hostInfo = buildHostInfo(Map.of(0, Set.of(10, 20, 30, 40, 50)), Map.of(0, 2048L));

        val result = allocator.allocate(hostInfo, 3, 128, 0);

        assertTrue(result.isPresent());
        assertEquals(3, result.get().getCpu().getCores().get(0).size(),
                     "Exactly 3 cores should be allocated when 5 are free");
    }

    // -------------------------------------------------------------------------
    // Memory allocation
    // -------------------------------------------------------------------------

    @Test
    void testAllocateMemoryMapsToSelectedNumaNode() {
        val hostInfo = buildHostInfo(
                Map.of(0, Set.of(0, 1), 1, Set.of(2, 3)),
                Map.of(0, 256L, 1, 2048L));

        val result = allocator.allocate(hostInfo, 1, 512, 0);

        assertTrue(result.isPresent());
        val memAlloc = result.get().getMemory().getMemoryInMB();
        assertEquals(1, memAlloc.size());
        assertEquals(512L, memAlloc.get(1),
                     "Memory should be allocated against NUMA node 1");
        assertNull(memAlloc.get(0), "NUMA node 0 should not have any memory allocation");
    }

    @Test
    void testAllocateMemoryUsesExactRequestedAmount() {
        val hostInfo = buildHostInfo(Map.of(0, Set.of(0, 1)), Map.of(0, 4096L));

        val result = allocator.allocate(hostInfo, 1, 1234, 0);

        assertTrue(result.isPresent());
        assertEquals(1234L, result.get().getMemory().getMemoryInMB().get(0));
    }

    // -------------------------------------------------------------------------
    // buildAllocatedNode — host metadata
    // -------------------------------------------------------------------------

    @Test
    void testBuildAllocatedNodePopulatesExecutorMetadata() {
        val hostInfo = buildHostInfo(Map.of(0, Set.of(0, 1)), Map.of(0, 2048L));

        val result = allocator.allocate(hostInfo, 1, 128, 0);

        assertTrue(result.isPresent());
        val node = result.get();
        assertEquals("EX-1", node.getExecutorId());
        assertEquals("test-host", node.getHostname());
        assertEquals(8080, node.getPort());
        assertEquals(NodeTransportType.HTTP, node.getTransportType());
        assertEquals(ExecutorState.ACTIVE, node.getExecutorState());
    }

    @Test
    void testBuildAllocatedNodeHasEmptyGpuAllocationWhenNoGpuRequired() {
        val hostInfo = buildHostInfo(Map.of(0, Set.of(0, 1)), Map.of(0, 2048L));

        val result = allocator.allocate(hostInfo, 1, 128, 0);

        assertTrue(result.isPresent());
        assertTrue(result.get().getGpu().getGpuIds().isEmpty(),
                   "GPU allocation should be empty when no GPUs were requested");
    }

    @Test
    void testBuildAllocatedNodeReturnsEmptyWhenNoEligibleNumaNode() {
        val hostInfo = buildHostInfo(Map.of(0, Set.of(0, 1)), Map.of(0, 2048L));

        val result = allocator.allocate(hostInfo, 3, 128, 0);

        assertTrue(result.isEmpty(), "Should return empty when no NUMA node satisfies CPU/memory requirements");
    }

    // -------------------------------------------------------------------------
    // No-GPU path (moved from GpuAwareNumaResourceAllocatorTest)
    // -------------------------------------------------------------------------

    @Test
    void testSelectNumaNodeWhenNoGpuRequested() {
        val hostInfo = buildHostInfoWithGpuTopology(Map.of("GPU-0", 0, "GPU-1", 1));

        val result = allocator.allocate(hostInfo, 1, 128, 0);

        assertTrue(result.isPresent());
        // Stub always picks first candidate; CPU should land on NUMA 0
        val cpuNumaNode = result.get().getCpu().getCores().keySet().iterator().next();
        assertEquals(0, cpuNumaNode);
        assertTrue(result.get().getGpu().getGpuIds().isEmpty(),
                   "No GPUs should be allocated when none were requested");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Builds an ExecutorHostInfo with the given free core sets and memory per NUMA node (no GPUs). */
    private ExecutorHostInfo buildHostInfo(
            Map<Integer, Set<Integer>> freeCoresPerNuma,
            Map<Integer, Long> freeMemoryPerNuma) {
        val numaNodes = new HashMap<Integer, ExecutorHostInfo.NumaNodeInfo>();
        freeCoresPerNuma.forEach((numaId, cores) -> {
            val info = numaNodes.computeIfAbsent(numaId, k -> new ExecutorHostInfo.NumaNodeInfo());
            cores.forEach(c -> info.getCores().put(c, ExecutorHostInfo.CoreState.FREE));
        });
        freeMemoryPerNuma.forEach((numaId, mem) ->
                numaNodes.computeIfAbsent(numaId, k -> new ExecutorHostInfo.NumaNodeInfo())
                         .getMemory().setAvailable(mem));

        return new ExecutorHostInfo("EX-1", buildNodeData(), numaNodes, new ExecutorHostInfo.HostGPUInfo());
    }

    /** Builds an ExecutorHostInfo with two NUMA nodes and the provided GPU→NUMA affinity map. */
    private ExecutorHostInfo buildHostInfoWithGpuTopology(Map<String, Integer> gpuNumaAffinities) {
        val freeMemory = new LinkedHashMap<Integer, Long>();
        freeMemory.put(0, 1024L);
        freeMemory.put(1, 1024L);

        val freeCores = new LinkedHashMap<Integer, Set<Integer>>();
        freeCores.put(0, Set.of(0, 1));
        freeCores.put(1, Set.of(2, 3));

        val usedMemory = freeMemory.keySet().stream()
                .collect(Collectors.toUnmodifiableMap(k -> k, k -> 0L));
        val physicalGpus = gpuNumaAffinities.entrySet().stream()
                .map(e -> new GPUInfo(e.getKey(), "NVIDIA", "H100", e.getValue(), true))
                .collect(Collectors.toUnmodifiableSet());

        val snapshot = new ExecutorResourceSnapshot(
                "EX-1",
                new AvailableCPU(freeCores, Map.of()),
                new AvailableMemory(freeMemory, usedMemory),
                new AvailableGPU(new ArrayList<>(gpuNumaAffinities.keySet()), List.of()),
                new PhysicalLayout(freeCores, freeMemory, physicalGpus));

        val nodeData = new ExecutorNodeData(
                "test-host", 8080, NodeTransportType.HTTP, new Date(),
                snapshot, List.of(), List.of(), List.of(),
                Set.of(), Map.of(), ExecutorState.ACTIVE);

        val numaNodes = new HashMap<Integer, ExecutorHostInfo.NumaNodeInfo>();
        freeCores.forEach((numaId, cores) -> {
            val info = numaNodes.computeIfAbsent(numaId, k -> new ExecutorHostInfo.NumaNodeInfo());
            cores.forEach(c -> info.getCores().put(c, ExecutorHostInfo.CoreState.FREE));
            info.getMemory().setAvailable(freeMemory.get(numaId));
        });

        val gpuInfo = new ExecutorHostInfo.HostGPUInfo();
        gpuNumaAffinities.keySet().forEach(id -> gpuInfo.getGpus().put(id, ExecutorHostInfo.CoreState.FREE));

        return new ExecutorHostInfo("EX-1", nodeData, numaNodes, gpuInfo);
    }

    private ExecutorNodeData buildNodeData() {
        val freeCores = Map.of(0, Set.of(0, 1));
        val freeMemory = Map.of(0, 2048L);
        val usedMemory = Map.of(0, 0L);
        val snapshot = new ExecutorResourceSnapshot(
                "EX-1",
                new AvailableCPU(freeCores, Map.of()),
                new AvailableMemory(freeMemory, usedMemory),
                new AvailableGPU(List.of(), List.of()),
                new PhysicalLayout(freeCores, freeMemory, Set.of()));
        return new ExecutorNodeData(
                "test-host", 8080, NodeTransportType.HTTP, new Date(),
                snapshot, List.of(), List.of(), List.of(),
                Set.of(), Map.of(), ExecutorState.ACTIVE);
    }
}



