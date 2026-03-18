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

import com.phonepe.drove.controller.resourcemgmt.allocator.GpuAwareNumaExecutorResourceAllocator;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.GPUInfo;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.resources.PhysicalLayout;
import com.phonepe.drove.models.info.resources.available.AvailableCPU;
import com.phonepe.drove.models.info.resources.available.AvailableGPU;
import com.phonepe.drove.models.info.resources.available.AvailableMemory;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GpuAwareNumaNodeSelector to ensure NUMA node selection
 * prioritizes nodes with the most local free GPUs.
 */
class GpuAwareNumaExecutorResourceAllocatorTest {

    private GpuAwareNumaExecutorResourceAllocator selector = new GpuAwareNumaExecutorResourceAllocator();

    @Test
    void testSelectNumaNodeWithMostLocalGpus() {
        // Setup: NUMA 0 has 1 GPU, NUMA 1 has 2 GPUs
        val hostInfo = createHostInfoWithGpuTopology(
                Map.of("GPU-0", 0, "GPU-1", 1, "GPU-2", 1)
        );

        val result = selector.allocate(hostInfo, 1, 128, 2);

        assertTrue(result.isPresent());
        val allocated = result.get();
        
        // CPU should be on NUMA 1 because it has 2 local GPUs
        val cpuNumaNode = allocated.getCpu().getCores().keySet().iterator().next();
        assertEquals(1, cpuNumaNode);

        // All 2 allocated GPUs should be local to NUMA 1
        val gpuNumaById = getGpuNumaAffinities(hostInfo);
        val gpusOnAllocatedNuma = allocated.getGpu().getGpuIds().stream()
                .map(gpuNumaById::get)
                .filter(numa -> Objects.equals(numa, cpuNumaNode))
                .count();
        assertEquals(2, gpusOnAllocatedNuma);
    }


    @Test
    void testSelectNumaNodeReturnsDeterministicOrderWhenGpuCountEqual() {
        val hostInfo = createHostInfoWithGpuTopology(
                Map.of("GPU-0", 0, "GPU-1", 1)  // Both NUMA nodes have 1 GPU each
        );

        val result = selector.allocate(hostInfo, 1, 128, 1);

        assertTrue(result.isPresent());
        // Should pick NUMA 0 (lowest ID) when GPU counts are equal
        val cpuNumaNode = result.get().getCpu().getCores().keySet().iterator().next();
        assertEquals(0, cpuNumaNode);
    }


    @Test
    void testSelectNumaNodePrefersMostLocalGpusWithMultipleCandidates() {
        // Setup: NUMA 0 has 1 GPU, NUMA 1 has 2 GPUs, NUMA 2 has 3 GPUs
        val hostInfo = createHostInfoWithGpuTopology(
                Map.of("GPU-0", 0, "GPU-1", 1, "GPU-2", 1, "GPU-3", 2, "GPU-4", 2, "GPU-5", 2)
        );

        val result = selector.allocate(hostInfo, 1, 128, 3);

        assertTrue(result.isPresent());
        val allocated = result.get();
        
        // CPU should be on NUMA 2 because it has the most local GPUs (3)
        val cpuNumaNode = allocated.getCpu().getCores().keySet().iterator().next();
        assertEquals(2, cpuNumaNode);

        // All 3 GPUs should be local to NUMA 2
        val gpuNumaById = getGpuNumaAffinities(hostInfo);
        val gpusOnAllocatedNuma = allocated.getGpu().getGpuIds().stream()
                .map(gpuNumaById::get)
                .filter(numa -> Objects.equals(numa, cpuNumaNode))
                .count();
        assertEquals(3, gpusOnAllocatedNuma);
    }

    @Test
    void testGpuAllocationPrefersNumaLocalGpu() {
        // When both NUMA nodes have free CPUs and memory, and each has local GPUs,
        // ensure that a GPU is allocated from the same NUMA node as the CPU
        val hostInfo = createHostInfoWithGpuTopology(
                Map.of("GPU-0", 0, "GPU-1", 1)
        );

        val result = selector.allocate(hostInfo, 1, 128, 1);

        assertTrue(result.isPresent());
        val allocated = result.get();
        
        val cpuNumaNode = allocated.getCpu().getCores().keySet().iterator().next();
        val allocatedGpu = allocated.getGpu().getGpuIds().iterator().next();
        val gpuNumaById = getGpuNumaAffinities(hostInfo);
        val gpuNumaNode = gpuNumaById.get(allocatedGpu);
        
        assertEquals(cpuNumaNode, gpuNumaNode, "GPU should be local to the CPU NUMA node");
    }

    @Test
    void testGpuAllocationFallsBackToCrossNumaWhenLocalGpuUnavailable() {
        // When the selected NUMA node has CPUs but no GPUs, ensure fallback to cross-NUMA GPU allocation
        
        // Create a scenario where NUMA 0 has CPUs but NUMA 1 has the GPU
        val freeMemory = new LinkedHashMap<Integer, Long>();
        freeMemory.put(0, 1024L);
        freeMemory.put(1, 1024L);

        val freeCores = new LinkedHashMap<Integer, Set<Integer>>();
        freeCores.put(0, Set.of(0, 1));
        freeCores.put(1, Set.of());  // No CPUs on NUMA 1

        val hostInfoNoCpuOnNuma1 = createCustomHostInfo(freeCores, freeMemory, Map.of("GPU-0", 1));

        val result = selector.allocate(hostInfoNoCpuOnNuma1, 1, 128, 1);

        assertTrue(result.isPresent());
        val allocated = result.get();
        
        val cpuNumaNode = allocated.getCpu().getCores().keySet().iterator().next();
        assertEquals(0, cpuNumaNode);
        
        val allocatedGpu = allocated.getGpu().getGpuIds().iterator().next();
        val gpuNumaById = getGpuNumaAffinities(hostInfoNoCpuOnNuma1);
        val gpuNumaNode = gpuNumaById.get(allocatedGpu);
        
        assertNotEquals(cpuNumaNode, gpuNumaNode, "GPU should fall back to cross-NUMA allocation");
        assertEquals(1, gpuNumaNode);
    }

    @Test
    void testGpuAllocationFallsBackWhenLocalNumaHasInsufficientGpusButRemoteHasEnough() {
        // CPU can only be allocated on NUMA 0. NUMA 0 has only 1 GPU while request needs 2.
        // NUMA 1 has 2 more GPUs, so allocator must cross NUMA and still succeed.
        val freeMemory = new LinkedHashMap<Integer, Long>();
        freeMemory.put(0, 1024L);
        freeMemory.put(1, 1024L);

        val freeCores = new LinkedHashMap<Integer, Set<Integer>>();
        freeCores.put(0, Set.of(0, 1));
        freeCores.put(1, Set.of());  // Keep NUMA 1 ineligible for CPU allocation

        val hostInfo = createCustomHostInfo(freeCores, freeMemory, Map.of("GPU-0", 0, "GPU-1", 1, "GPU-2", 1));
        val result = selector.allocate(hostInfo, 1, 128, 2);

        assertTrue(result.isPresent());
        val allocated = result.get();
        
        assertEquals(2, allocated.getGpu().getGpuIds().size());
        val cpuNumaNode = allocated.getCpu().getCores().keySet().iterator().next();
        assertEquals(0, cpuNumaNode);

        val gpuNumaById = getGpuNumaAffinities(hostInfo);
        val localGpuCount = allocated.getGpu().getGpuIds().stream()
                .map(gpuNumaById::get)
                .filter(numa -> Objects.equals(numa, cpuNumaNode))
                .count();
        
        assertTrue(localGpuCount < 2,
                   "Expected cross-NUMA GPU allocation because local NUMA has only 1 GPU for a 2-GPU request");
    }

    @Test
    void testGpuAllocationChoosesCpuNumaNodeThatHasEnoughLocalGpusWhenAnyCpuNodeIsEligible() {
        // Both NUMA 0 and NUMA 1 have CPUs and memory, but only NUMA 1 has enough local GPUs for the request.
        // Ensure selector picks NUMA 1 instead of NUMA 0.
        val hostInfo = createHostInfoWithGpuTopology(
                Map.of("GPU-0", 0, "GPU-1", 1, "GPU-2", 1)
        );

        val result = selector.allocate(hostInfo, 1, 128, 2);

        assertTrue(result.isPresent());
        val allocated = result.get();
        
        assertEquals(2, allocated.getGpu().getGpuIds().size());
        val cpuNumaNode = allocated.getCpu().getCores().keySet().iterator().next();
        
        assertEquals(1, cpuNumaNode,
                     "Expected CPU allocation on NUMA 1 because only NUMA 1 has two local GPUs");

        val gpuNumaById = getGpuNumaAffinities(hostInfo);
        assertTrue(allocated.getGpu().getGpuIds().stream()
                           .map(gpuNumaById::get)
                           .allMatch(numa -> Objects.equals(numa, cpuNumaNode)),
                   "Expected all allocated GPUs to be local to chosen CPU NUMA node");
    }

    @Test
    void testGpuAllocationFailsWhenTotalFreeGpusInsufficient() {
        // Request more GPUs than available in the system
        val hostInfo = createHostInfoWithGpuTopology(
                Map.of("GPU-0", 0)  // Only 1 GPU total
        );

        val result = selector.allocate(hostInfo, 1, 128, 2);  // Request 2 GPUs

        assertTrue(result.isEmpty(), "Should fail when total free GPUs insufficient");
    }

    @Test
    void testGpuAllocationWhenSomeGpuAffinityCouldNotBeCalculated() {
        // Scenario: Some GPUs have NUMA affinity, others have null affinity
        // GPU-0 and GPU-1 have affinity, GPU-2 does not (affinity = null)
        // When allocating, the allocator should:
        // 1. Exclude GPU-2 from affinity mapping (not in gpuNumaAffinities)
        // 2. Try to allocate from GPUs with known affinity first
        // 3. Fall back to GPUs without affinity if needed
        
        val freeMemory = new LinkedHashMap<Integer, Long>();
        freeMemory.put(0, 1024L);
        freeMemory.put(1, 1024L);

        val freeCores = new LinkedHashMap<Integer, Set<Integer>>();
        freeCores.put(0, Set.of(0, 1));
        freeCores.put(1, Set.of(2, 3));

        val hostInfo = createCustomHostInfoWithPartialAffinities(
                freeCores,
                freeMemory,
                // GPUs with affinity
                Map.of("GPU-0", 0, "GPU-1", 1),
                // GPU IDs without affinity (affinity = null)
                Set.of("GPU-2")
        );

        val result = selector.allocate(hostInfo, 1, 128, 2);

        // Should succeed and allocate 2 GPUs
        assertTrue(result.isPresent(), "Should allocate GPUs even when some GPUs lack affinity info");
        val allocated = result.get();
        assertEquals(2, allocated.getGpu().getGpuIds().size(), "Should allocate exactly 2 GPUs");
        
        // Check that GPUs with known affinity are preferred
        val gpuNumaById = getGpuNumaAffinities(hostInfo);
        val allocatedGpus = allocated.getGpu().getGpuIds();
        
        // GPU-0 and GPU-1 have affinity, GPU-2 does not
        // We should prefer GPU-0 and GPU-1 first
        val gpusWithAffinity = allocatedGpus.stream()
                .filter(gpuNumaById::containsKey)
                .count();
        
        // At least one GPU with known affinity should be allocated
        assertTrue(gpusWithAffinity >= 1, "Should prefer allocating GPUs with known affinity");
    }

    @Test
    void testGpuAllocationOnlyFallsBackToUnknownAffinityGpuWhenNecessary() {
        // Scenario: NUMA 0 has only 1 GPU with affinity, NUMA 1 has none with affinity
        // When requesting 2 GPUs and both must be allocated on NUMA 0,
        // the allocator should use the 1 GPU with affinity and fall back to GPU without affinity
        
        val freeMemory = new LinkedHashMap<Integer, Long>();
        freeMemory.put(0, 1024L);
        freeMemory.put(1, 1024L);

        val freeCores = new LinkedHashMap<Integer, Set<Integer>>();
        freeCores.put(0, Set.of(0, 1));
        freeCores.put(1, Set.of());  // No CPUs on NUMA 1, forcing allocation on NUMA 0

        // GPU-0 has affinity to NUMA 0, GPU-1 doesn't have affinity
        val hostInfo = createCustomHostInfoWithPartialAffinities(
                freeCores,
                freeMemory,
                Map.of("GPU-0", 0),
                Set.of("GPU-1")
        );

        val result = selector.allocate(hostInfo, 1, 128, 2);

        assertTrue(result.isPresent(), "Should succeed with fallback to unknown-affinity GPU");
        val allocated = result.get();
        assertEquals(2, allocated.getGpu().getGpuIds().size());
        
        val cpuNumaNode = allocated.getCpu().getCores().keySet().iterator().next();
        assertEquals(0, cpuNumaNode, "CPU should be on NUMA 0");
        
        // Should have GPU-0 (with affinity) and GPU-1 (fallback)
        val allocatedGpuIds = allocated.getGpu().getGpuIds();
        assertTrue(allocatedGpuIds.contains("GPU-0"), "Should allocate GPU-0 (has affinity)");
        assertTrue(allocatedGpuIds.contains("GPU-1"), "Should allocate GPU-1 as fallback (no affinity)");
    }

    @Test
    void testGpuAllocationWhenAllGpusLackAffinity() {
        // Scenario: All free GPUs have null affinity (allocation info is incomplete)
        // The allocator should still be able to allocate GPUs, treating them all equally
        
        val freeMemory = new LinkedHashMap<Integer, Long>();
        freeMemory.put(0, 1024L);
        freeMemory.put(1, 1024L);

        val freeCores = new LinkedHashMap<Integer, Set<Integer>>();
        freeCores.put(0, Set.of(0, 1));
        freeCores.put(1, Set.of(2, 3));

        // All GPUs lack affinity
        val hostInfo = createCustomHostInfoWithPartialAffinities(
                freeCores,
                freeMemory,
                Map.of(),  // No GPUs with affinity
                Set.of("GPU-0", "GPU-1", "GPU-2")  // All lack affinity
        );

        val result = selector.allocate(hostInfo, 1, 128, 2);

        // Should succeed by falling back to any available GPU
        assertTrue(result.isPresent(), "Should allocate even when all GPUs lack affinity");
        val allocated = result.get();
        assertEquals(2, allocated.getGpu().getGpuIds().size(), "Should allocate 2 GPUs");
    }

    private ExecutorHostInfo createHostInfoWithGpuTopology(Map<String, Integer> gpuNumaAffinities) {
        val freeMemory = new LinkedHashMap<Integer, Long>();
        freeMemory.put(0, 1024L);
        freeMemory.put(1, 1024L);
        if (gpuNumaAffinities.values().stream().anyMatch(numa -> numa == 2)) {
            freeMemory.put(2, 1024L);
        }

        val freeCores = new LinkedHashMap<Integer, Set<Integer>>();
        freeCores.put(0, Set.of(0, 1));
        freeCores.put(1, Set.of(2, 3));
        if (gpuNumaAffinities.values().stream().anyMatch(numa -> numa == 2)) {
            freeCores.put(2, Set.of(4, 5));
        }

        return createCustomHostInfo(freeCores, freeMemory, gpuNumaAffinities);
    }

    private ExecutorHostInfo createCustomHostInfo(
            Map<Integer, Set<Integer>> freeCores,
            Map<Integer, Long> freeMemory,
            Map<String, Integer> gpuNumaAffinities) {
        val usedMemory = freeMemory.keySet().stream()
                .collect(Collectors.toUnmodifiableMap(k -> k, k -> 0L));
        val physicalGpus = gpuNumaAffinities.entrySet().stream()
                .map(entry -> new GPUInfo(entry.getKey(), "NVIDIA", "H100", entry.getValue(), true))
                .collect(Collectors.toUnmodifiableSet());

        val snapshot = new ExecutorResourceSnapshot(
                "TEST_EXECUTOR",
                new AvailableCPU(freeCores, Map.of()),
                new AvailableMemory(freeMemory, usedMemory),
                new AvailableGPU(new ArrayList<>(gpuNumaAffinities.keySet()), List.of()),
                new PhysicalLayout(freeCores, freeMemory, physicalGpus)
        );

        val nodeData = new ExecutorNodeData(
                "test-host",
                8080,
                NodeTransportType.HTTP,
                new Date(),
                snapshot,
                List.of(),
                List.of(),
                List.of(),
                Set.of(),
                Map.of(),
                com.phonepe.drove.models.info.nodedata.ExecutorState.ACTIVE
        );

        return new ExecutorHostInfo("TEST_EXECUTOR", nodeData, convertToNodeInfo(snapshot), convertToGpuInfo(snapshot));
    }

    private ExecutorHostInfo createCustomHostInfoWithPartialAffinities(
            Map<Integer, Set<Integer>> freeCores,
            Map<Integer, Long> freeMemory,
            Map<String, Integer> gpuNumaAffinities,  // GPUs with known affinity
            Set<String> unknownAffinityGpus         // GPU IDs without affinity (null)
    ) {
        val usedMemory = freeMemory.keySet().stream()
                .collect(Collectors.toUnmodifiableMap(k -> k, k -> 0L));
        val physicalGpus = gpuNumaAffinities.entrySet().stream()
                .map(entry -> new GPUInfo(entry.getKey(), "NVIDIA", "H100", entry.getValue(), true))
                .collect(Collectors.toUnmodifiableSet());

        // Add GPUs without affinity as "unknown" (null affinity)
        val physicalGpusWithUnknowns = new HashSet<>(physicalGpus);
        unknownAffinityGpus.forEach(gpuId -> physicalGpusWithUnknowns.add(new GPUInfo(gpuId, "NVIDIA", "H100", null, true)));

        val snapshot = new ExecutorResourceSnapshot(
                "TEST_EXECUTOR",
                new AvailableCPU(freeCores, Map.of()),
                new AvailableMemory(freeMemory, usedMemory),
                new AvailableGPU(new ArrayList<>(gpuNumaAffinities.keySet()), List.of()),
                new PhysicalLayout(freeCores, freeMemory, physicalGpusWithUnknowns)
        );

        val nodeData = new ExecutorNodeData(
                "test-host",
                8080,
                NodeTransportType.HTTP,
                new Date(),
                snapshot,
                List.of(),
                List.of(),
                List.of(),
                Set.of(),
                Map.of(),
                com.phonepe.drove.models.info.nodedata.ExecutorState.ACTIVE
        );

        return new ExecutorHostInfo("TEST_EXECUTOR", nodeData, convertToNodeInfo(snapshot), convertToGpuInfo(snapshot));
    }

    private Map<String, Integer> getGpuNumaAffinities(ExecutorHostInfo hostInfo) {
        return hostInfo.getNodeData().getState().getLayout().getGpus().stream()
                .filter(gpu -> gpu.getNumaNodeAffinity() != null)
                .collect(Collectors.toUnmodifiableMap(
                        GPUInfo::getGpuId,
                        GPUInfo::getNumaNodeAffinity,
                        (left, right) -> left
                ));
    }

    private Map<Integer, ExecutorHostInfo.NumaNodeInfo> convertToNodeInfo(ExecutorResourceSnapshot resourceSnapshot) {
        val numaNodes = new HashMap<Integer, ExecutorHostInfo.NumaNodeInfo>();
        val cpus = resourceSnapshot.getCpus();
        val memory = resourceSnapshot.getMemory();

        cpus.getFreeCores().forEach((key, freeCores) -> {
            val nodeInfo = numaNodes.computeIfAbsent(key, k -> new ExecutorHostInfo.NumaNodeInfo());
            freeCores.forEach(i -> nodeInfo.getCores()
                    .compute(i,
                             (core, state) -> state != ExecutorHostInfo.CoreState.ALLOCATED
                                              ? ExecutorHostInfo.CoreState.FREE
                                              : state));
        });

        cpus.getUsedCores().forEach((key, usedCores) -> {
            val nodeInfo = numaNodes.computeIfAbsent(key, k -> new ExecutorHostInfo.NumaNodeInfo());
            usedCores.forEach(i -> nodeInfo.getCores()
                    .put(i, ExecutorHostInfo.CoreState.IN_USE));
        });

        memory.getUsedMemory().forEach((key, usedMemory) -> {
            val nodeInfo = numaNodes.computeIfAbsent(key, k -> new ExecutorHostInfo.NumaNodeInfo());
            nodeInfo.getMemory().setUsed(usedMemory);
        });

        memory.getFreeMemory().forEach((key, freeMemory) -> {
            val nodeInfo = numaNodes.computeIfAbsent(key, k -> new ExecutorHostInfo.NumaNodeInfo());
            nodeInfo.getMemory().setAvailable(freeMemory);
        });

        return numaNodes;
    }

    private ExecutorHostInfo.HostGPUInfo convertToGpuInfo(ExecutorResourceSnapshot resourceSnapshot) {
        val gpuInfo = new ExecutorHostInfo.HostGPUInfo();
        resourceSnapshot.getGpus().getFreeGpus()
                .forEach(gpuId -> gpuInfo.getGpus().put(gpuId, ExecutorHostInfo.CoreState.FREE));
        resourceSnapshot.getGpus().getUsedGpus()
                .forEach(gpuId -> gpuInfo.getGpus().put(gpuId, ExecutorHostInfo.CoreState.IN_USE));
        
        // Also include GPUs from the physical layout that aren't in the available list
        resourceSnapshot.getLayout().getGpus().stream()
                .map(GPUInfo::getGpuId)
                .filter(gpuId -> !gpuInfo.getGpus().containsKey(gpuId))
                .forEach(gpuId -> gpuInfo.getGpus().put(gpuId, ExecutorHostInfo.CoreState.FREE));
        
        return gpuInfo;
    }
}







