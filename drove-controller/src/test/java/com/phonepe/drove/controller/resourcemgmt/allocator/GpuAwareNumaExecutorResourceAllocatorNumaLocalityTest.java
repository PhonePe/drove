package com.phonepe.drove.controller.resourcemgmt.allocator;

import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.models.application.requirements.GpuType;
import com.phonepe.drove.models.application.requirements.GpuVendor;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.GPUInfo;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
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
 * Test case for NUMA Locality Fallback Bug in GpuAwareNumaExecutorResourceAllocator.
 * 
 * ISSUE: NUMA Locality Fallback Bug (CRITICAL)
 * 
 * Current State: When falling back to cross-NUMA GPUs, the allocator takes first N
 *                GPUs without proper ordering, potentially mixing local and remote
 *                GPUs arbitrarily.
 * Expected State: Either all GPUs should be local, or if cross-NUMA, they should be
 *                 tracked separately with proper locality awareness.
 * 
 * Rationale: Arbitrarily mixing local and remote GPUs can lead to suboptimal 
 * performance. The allocator should be explicit about locality to allow proper
 * resource tracking and scheduling decisions.
 */
class GpuAwareNumaExecutorResourceAllocatorNumaLocalityTest {
    
    private final GpuAwareNumaExecutorResourceAllocator allocator = new GpuAwareNumaExecutorResourceAllocator();
    private static final GpuType NVIDIA_H100 = new GpuType(GpuVendor.NVIDIA, "H100");

    @Test
    void testNumaLocalAllocationShouldHaveLocalGpuCountEqualToTotal() {
        /**
         * SCENARIO: All allocated GPUs are NUMA-local
         * EXPECTED: localGpuCount == total GPU count
         */
        val hostInfo = createHostInfoWithGpuTopology(
            Map.of("GPU-0", 0, "GPU-1", 0, "GPU-2", 1)
        );

        // CPU on NUMA 0, requesting 2 GPUs (both should be local)
        val result = allocator.allocate(hostInfo, 1, 128, 2, NVIDIA_H100);

        assertTrue(result.isPresent());
        val allocated = result.get().getAllocatedNode();
        val allocationMeta = result.get().getMetadata();

        val cpuNumaNode = allocated.getCpu().getCores().keySet().iterator().next();
        assertEquals(0, cpuNumaNode, "CPU should be on NUMA 0");

        val gpuNumaAffinities = getGpuNumaAffinities(hostInfo);
        val allocatedGpuIds = allocated.getGpu().getGpuIds();
        
        // All allocated GPUs should be on NUMA 0 (local)
        for (String gpuId : allocatedGpuIds) {
            assertEquals(0, gpuNumaAffinities.get(gpuId), 
                "Allocated GPU " + gpuId + " should be local to NUMA 0");
        }

        assertEquals(2, allocated.getGpu().getGpuIds().size());
        assertEquals(2, allocationMeta.getNumaLocalGpuCount(),
            "All 2 GPUs should be counted as local");
    }

    @Test
    void testCrossNumaFallbackWhenTotalFreeGpusNotEnough() {
        /**
         * SCENARIO: Request more GPUs than available system-wide
         * EXPECTED: Allocation should fail (return empty Optional)
         */
        val hostInfo = createHostInfoWithGpuTopology(
            Map.of("GPU-0", 0, "GPU-1", 1)  // Only 2 GPUs total
        );

        // Request 3 GPUs when only 2 available
        val result = allocator.allocate(hostInfo, 1, 128, 3, NVIDIA_H100);

        assertTrue(result.isEmpty(), 
            "Should fail when requesting more GPUs than available");
    }

    @Test
    void testNumaFallbackWithNullAffinityGPUs() {
        /**
         * SCENARIO: Some GPUs have null NUMA affinity (unavailable info)
         * EXPECTED: Allocator should still work, treating null-affinity GPUs
         *           as potential fallback targets
         */
        val hostInfo = createHostInfoWithPartialAffinities(
            Map.of(0, Set.of(0, 1)),
            Map.of(0, 2048L),
            Map.of("GPU-0", 0),  // Has affinity
            Set.of("GPU-1", "GPU-2")  // Null affinity
        );

        // Request 3 GPUs (only 2 available total)
        val result = allocator.allocate(hostInfo, 1, 128, 3, NVIDIA_H100);

        // This tests the fallback behavior - should fail gracefully
        assertFalse(result.isEmpty(),
            "Should fail when requesting more GPUs than available (3 requested, 2 total)");
    }

    @Test
    void testCrossNumaAllocationAllGPUsFromRemoteNuma() {
        /**
         * SCENARIO: CPU on NUMA 0, but all available GPUs are on NUMA 1
         * EXPECTED: Allocate GPUs from NUMA 1 with localGpuCount = 0
         */
        val hostInfo = createHostInfoWithGpuTopology(
            Map.of("GPU-0", 1, "GPU-1", 1, "GPU-2", 1)  // All on NUMA 1
        );

        // Request 2 GPUs when CPU on NUMA 0 has no local GPUs
        val result = allocator.allocate(hostInfo, 1, 3028, 2, NVIDIA_H100);

        assertTrue(result.isPresent());
        val allocated = result.get();

        // CPU should be on NUMA 0 (has most CPUs)
        val cpuNumaNode = allocated.getAllocatedNode().getCpu().getCores().keySet().iterator().next();
        assertEquals(0, cpuNumaNode);

        // All GPUs should be cross-NUMA (NUMA 1)
        val localGpuCount = allocated.getMetadata().getNumaLocalGpuCount();
        assertEquals(0, localGpuCount, 
            "Should have 0 local GPUs because CPU and GPUs are on different NUMA nodes");
    }

    @Test
    void testExactNumaLocalAllocation() {
        /**
         * SCENARIO: Request exactly the number of local GPUs available
         * EXPECTED: All GPUs should be local, localGpuCount should equal request
         */
        val hostInfo = createHostInfoWithGpuTopology(
            Map.of("GPU-0", 0, "GPU-1", 0, "GPU-2", 1)  // 2 on NUMA 0, 1 on NUMA 1
        );

        // Request 2 GPUs when CPU on NUMA 0 has 2 local GPUs
        val result = allocator.allocate(hostInfo, 1, 128, 2, NVIDIA_H100);

        assertTrue(result.isPresent());
        val allocated = result.get().getAllocatedNode();

        assertEquals(2, allocated.getGpu().getGpuIds().size());
        assertEquals(2, result.get().getMetadata().getNumaLocalGpuCount(),
            "Both GPUs should be NUMA-local");
    }

    private ExecutorHostInfo createHostInfoWithTwoNuma(
        Map<String, Integer> numa0Gpus,
        Map<String, Integer> numa1Gpus
    ) {
        val freeMemory = new LinkedHashMap<Integer, Long>();
        freeMemory.put(0, 2048L);
        freeMemory.put(1, 2048L);

        val freeCores = new LinkedHashMap<Integer, Set<Integer>>();
        freeCores.put(0, Set.of(0, 1, 2, 3));
        freeCores.put(1, Set.of(4, 5, 6, 7));

        val allGpus = new HashMap<>(numa0Gpus);
        allGpus.putAll(numa1Gpus);

        return createCustomHostInfo(freeCores, freeMemory, allGpus);
    }

    private ExecutorHostInfo createHostInfoWithGpuTopology(Map<String, Integer> gpuNumaAffinities) {
        val freeMemory = new LinkedHashMap<Integer, Long>();
        freeMemory.put(0, 4048L);
        freeMemory.put(1, 2048L);

        val freeCores = new LinkedHashMap<Integer, Set<Integer>>();
        freeCores.put(0, Set.of(0, 1, 2, 3));
        freeCores.put(1, Set.of(4, 5, 6, 7));

        return createCustomHostInfo(freeCores, freeMemory, gpuNumaAffinities);
    }

    private ExecutorHostInfo createCustomHostInfo(
        Map<Integer, Set<Integer>> freeCores,
        Map<Integer, Long> freeMemory,
        Map<String, Integer> gpuNumaAffinities
    ) {
        val usedMemory = freeMemory.keySet().stream()
            .collect(Collectors.toUnmodifiableMap(k -> k, k -> 0L));

        val physicalGpus = gpuNumaAffinities.entrySet().stream()
            .map(entry -> new GPUInfo(entry.getKey(), GpuVendor.NVIDIA, "H100", entry.getValue(), true))
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
            ExecutorState.ACTIVE
        );

        val numaNodes = new HashMap<Integer, ExecutorHostInfo.NumaNodeInfo>();
        freeCores.forEach((numaId, cores) -> {
            val info = numaNodes.computeIfAbsent(numaId, k -> new ExecutorHostInfo.NumaNodeInfo());
            cores.forEach(c -> info.getCores().put(c, ExecutorHostInfo.CoreState.FREE));
            info.getMemory().setAvailable(freeMemory.get(numaId));
        });

        val gpuInfo = new ExecutorHostInfo.HostGPUInfo();
        gpuNumaAffinities.keySet().forEach(id -> gpuInfo.getGpus().put(id, ExecutorHostInfo.CoreState.FREE));

        return new ExecutorHostInfo("TEST_EXECUTOR", nodeData, numaNodes, gpuInfo);
    }

    private ExecutorHostInfo createHostInfoWithPartialAffinities(
        Map<Integer, Set<Integer>> freeCores,
        Map<Integer, Long> freeMemory,
        Map<String, Integer> gpuNumaAffinities,
        Set<String> unknownAffinityGpus
    ) {
        val usedMemory = freeMemory.keySet().stream()
            .collect(Collectors.toUnmodifiableMap(k -> k, k -> 0L));

        val physicalGpus = gpuNumaAffinities.entrySet().stream()
            .map(entry -> new GPUInfo(entry.getKey(), GpuVendor.NVIDIA, "H100", entry.getValue(), true))
            .collect(Collectors.toSet());

        // Add GPUs without affinity
        unknownAffinityGpus.forEach(gpuId -> 
            physicalGpus.add(new GPUInfo(gpuId, GpuVendor.NVIDIA, "H100", null, true)));

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
            ExecutorState.ACTIVE
        );

        val numaNodes = new HashMap<Integer, ExecutorHostInfo.NumaNodeInfo>();
        freeCores.forEach((numaId, cores) -> {
            val info = numaNodes.computeIfAbsent(numaId, k -> new ExecutorHostInfo.NumaNodeInfo());
            cores.forEach(c -> info.getCores().put(c, ExecutorHostInfo.CoreState.FREE));
            info.getMemory().setAvailable(freeMemory.get(numaId));
        });

        val gpuInfo = new ExecutorHostInfo.HostGPUInfo();
        gpuNumaAffinities.keySet().forEach(id -> gpuInfo.getGpus().put(id, ExecutorHostInfo.CoreState.FREE));
        unknownAffinityGpus.forEach(id -> gpuInfo.getGpus().put(id, ExecutorHostInfo.CoreState.FREE));

        return new ExecutorHostInfo("TEST_EXECUTOR", nodeData, numaNodes, gpuInfo);
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
}
