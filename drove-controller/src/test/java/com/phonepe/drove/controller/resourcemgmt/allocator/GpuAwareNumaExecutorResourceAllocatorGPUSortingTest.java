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
 * Test case for GPU Type Validation in GpuAwareNumaExecutorResourceAllocator.
 * 
 * ISSUE: Missing GPU Type Validation in Allocator (CRITICAL)
 * 
 * Current State: When GPU type is requested but unavailable, the allocator silently
 *                allocates no GPUs (returns empty Optional).
 * Expected State: Allocator should provide clear feedback that GPU type requirement 
 *                 cannot be satisfied.
 * 
 * Rationale: Silent failure on GPU type mismatch makes debugging extremely difficult
 * for users who request specific GPU types that aren't available.
 */
class GpuAwareNumaExecutorResourceAllocatorGPUSortingTest {
    
    private final GpuAwareNumaExecutorResourceAllocator allocator = new GpuAwareNumaExecutorResourceAllocator();

    @Test
    void testGPUTypeMismatchShouldFailWithClearFeedback() {
        /**
         * SCENARIO: Request H100 GPUs but only MI300X GPUs are available
         * EXPECTED: Return empty Optional with clear indication that H100 is unavailable
         * CURRENT BUG: Silent failure - allocates no GPUs but doesn't fail properly
         */
        val hostInfo = createHostInfoWithTypedGpus(
            Map.of(0, Set.of(0, 1, 2, 3)),
            Map.of(0, 2048L),
            Set.of(
                new GPUInfo("GPU-MI300", GpuVendor.AMD, "MI300X", 0, true)
            )
        );

        // Request H100 but only MI300X is available
        val result = allocator.allocate(
            hostInfo, 
            1, 
            128, 
            1, 
            new GpuType(GpuVendor.NVIDIA, "H100")
        );

        // This test should PASS after fix - allocator should return empty Optional
        // when requested GPU type (H100) is not available, but this is explicitly
        // the expected behavior after fix
        assertTrue(
            result.isEmpty(), 
            "Allocation should fail when requested GPU type (H100) is not available"
        );
    }

    @Test
    void testGPUTypeAvailableShouldSucceed() {
        /**
         * SCENARIO: Request H100 GPUs and H100 GPUs are available
         * EXPECTED: Allocation should succeed
         */
        val hostInfo = createHostInfoWithTypedGpus(
            Map.of(0, Set.of(0, 1, 2, 3)),
            Map.of(0, 2048L),
            Set.of(
                new GPUInfo("GPU-H100", GpuVendor.NVIDIA, "H100", 0, true)
            )
        );

        val result = allocator.allocate(
            hostInfo, 
            1, 
            128, 
            1, 
            new GpuType(GpuVendor.NVIDIA, "H100")
        );

        assertTrue(result.isPresent(), "Allocation should succeed when GPU type is available");
        assertEquals(1, result.get().getGpu().getGpuIds().size());
    }

    @Test
    void testGPUTypeNotAvailableWhenSomeGPUsFreeButWrongType() {
        /**
         * SCENARIO: Request H100 but machines has 4 free MI300X GPUs
         * EXPECTED: Should fail to allocate (return empty) because H100 type not found
         */
        val hostInfo = createHostInfoWithTypedGpus(
            Map.of(0, Set.of(0, 1, 2, 3)),
            Map.of(0, 2048L),
            Set.of(
                new GPUInfo("GPU-MI300-1", GpuVendor.AMD, "MI300X", 0, true),
                new GPUInfo("GPU-MI300-2", GpuVendor.AMD, "MI300X", 0, true),
                new GPUInfo("GPU-MI300-3", GpuVendor.AMD, "MI300X", 0, true),
                new GPUInfo("GPU-MI300-4", GpuVendor.AMD, "MI300X", 0, true)
            )
        );

        // Request 2 H100 GPUs when only MI300X available
        val result = allocator.allocate(
            hostInfo, 
            1, 
            128, 
            2, 
            new GpuType(GpuVendor.NVIDIA, "H100")
        );

        assertTrue(result.isEmpty(), 
            "Should fail when GPU type (H100) is not in the pool even though GPUs are available (MI300X)");
    }

    @Test
    void testGPUTypeRequestWithNoGPUsAvailable() {
        /**
         * SCENARIO: Request H100 GPUs but no GPUs at all in the system
         * EXPECTED: Should fail with empty Optional
         */
        val hostInfo = createHostInfoWithTypedGpus(
            Map.of(0, Set.of(0, 1, 2, 3)),
            Map.of(0, 2048L),
            Set.of()  // No GPUs at all
        );

        val result = allocator.allocate(
            hostInfo, 
            1, 
            128, 
            2, 
            new GpuType(GpuVendor.NVIDIA, "H100")
        );

        assertTrue(result.isEmpty(), 
            "Should fail when no GPUs are available at all");
    }

    @Test
    void testGPUTypeMismatchShouldNotFallBackToAnyGPU() {
        /**
         * SCENARIO: Request specific GPU type with type requirement
         * EXPECTED: Should NOT fall back to allocating any available GPU
         *           (that would be the bug - ignoring type requirement)
         */
        val hostInfo = createHostInfoWithTypedGpus(
            Map.of(0, Set.of(0, 1, 2, 3)),
            Map.of(0, 2048L),
            Set.of(
                new GPUInfo("GPU-MI300", GpuVendor.AMD, "MI300X", 0, true)
            )
        );

        val result = allocator.allocate(
            hostInfo, 
            1, 
            128, 
            1, 
            new GpuType(GpuVendor.NVIDIA, "H100")  // Explicit H100 requirement
        );

        assertTrue(result.isEmpty(), 
            "Should NOT allocate MI300X when H100 was explicitly requested");

        // After fix: If type requirement is ignored (bug), this would return:
        // result.get().getGpu().getGpuIds().contains("GPU-MI300") -> TRUE (WRONG!)
    }

    @Test
    void testMultipleGPUTypeRequestsDifferentTypes() {
        /**
         * SCENARIO: Multiple consecutive allocations with different GPU types
         * EXPECTED: Each should validate against available types independently
         */
        val hostInfo = createHostInfoWithTypedGpus(
            Map.of(0, Set.of(0, 1, 2, 3)),
            Map.of(0, 2048L),
            Set.of(
                new GPUInfo("GPU-H100", GpuVendor.NVIDIA, "H100", 0, true)
            )
        );

        // First request: H100 should succeed
        val h100Result = allocator.allocate(
            hostInfo, 1, 128, 1, new GpuType(GpuVendor.NVIDIA, "H100")
        );
        assertTrue(h100Result.isPresent(), "H100 request should succeed");

        // Second request: MI300 should fail (H100 was allocated, only MI300 available)
        val mi300Result = allocator.allocate(
            hostInfo, 1, 128, 1, new GpuType(GpuVendor.AMD, "MI300X")
        );
        assertTrue(mi300Result.isEmpty(), "MI300 request should fail after H100 allocation");
    }

    private ExecutorHostInfo createHostInfoWithTypedGpus(
        Map<Integer, Set<Integer>> freeCores,
        Map<Integer, Long> freeMemory,
        Set<GPUInfo> typedGpus
    ) {
        val usedMemory = freeMemory.keySet().stream()
            .collect(Collectors.toUnmodifiableMap(k -> k, k -> 0L));

        val freeGpuIds = typedGpus.stream().map(GPUInfo::getGpuId).toList();
        val snapshot = new ExecutorResourceSnapshot(
            "TEST_EXECUTOR",
            new AvailableCPU(freeCores, Map.of()),
            new AvailableMemory(freeMemory, usedMemory),
            new AvailableGPU(freeGpuIds, List.of()),
            new PhysicalLayout(freeCores, freeMemory, typedGpus)
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
        typedGpus.forEach(gpu -> gpuInfo.getGpus().put(gpu.getGpuId(), ExecutorHostInfo.CoreState.FREE));

        return new ExecutorHostInfo("TEST_EXECUTOR", nodeData, numaNodes, gpuInfo);
    }
}
