package com.phonepe.drove.models.info.resources.allocation;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for GPUAllocation Missing Validation.
 * 
 * ISSUE: GPUAllocation lacks proper validation for:
 *   - localGpuCount validation
 *   - Explicit locality tracking
 *   - Consistency between gpuIds and localGpuCount
 * 
 * Current State: No validation on GPUAllocation fields
 * Expected State: Should validate:
 *   - localGpuCount should not exceed total GPU count
 *   - localGpuCount should be non-negative
 *   - gpuIds should not be null or empty (when Gpu is requested)
 * 
 * Rationale: Without validation, inconsistencies can occur:
 *   - localGpuCount > total GPUs (impossible)
 *   - localGpuCount negative (data corruption)
 *   - Discrepancies in locality tracking
 */
class GPUAllocationValidationTest {

    @Test
    void testGPUAllocationLocalGpuCountCannotExceedTotal() {
        /**
         * ISSUE: No validation that localGpuCount <= total GPU count
         * 
         * EXPECTED: Should throw IllegalArgumentException if localGpuCount > gpuIds.size()
         */
        
        Set<String> gpuIds = Set.of("GPU-0", "GPU-1");  // 2 GPUs
        long localGpuCount = 5;  // Claims 5 are local, but only 2 total

        // After fix: should throw exception
        // Current (no validation): constructor accepts invalid data
        
        assertThrows(IllegalArgumentException.class, () -> {
            new GPUAllocation(gpuIds, localGpuCount);
        }, "Should reject localGpuCount > total GPU count");
    }

    @Test
    void testGPUAllocationLocalGpuCountCannotBeNegative() {
        /**
         * ISSUE: No validation that localGpuCount is non-negative
         * 
         * EXPECTED: Should throw IllegalArgumentException if localGpuCount < 0
         */
        
        Set<String> gpuIds = Set.of("GPU-0", "GPU-1");
        long localGpuCount = -1;

        // After fix: should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            new GPUAllocation(gpuIds, localGpuCount);
        }, "Should reject negative localGpuCount");
    }

    @Test
    void testGPUAllocationLocalGpuCountCanBeZero() {
        /**
         * ISSUE: localGpuCount = 0 should be valid (all GPUs are cross-NUMA)
         * 
         * EXPECTED: Should be valid to have 0 NUMA-local GPUs
         */
        
        Set<String> gpuIds = Set.of("GPU-0", "GPU-1");
        long localGpuCount = 0;

        // This should be valid
        GPUAllocation allocation = new GPUAllocation(gpuIds, localGpuCount);
        
        assertEquals(0, allocation.getLocalGpuCount());
        assertEquals(2, allocation.getGpuIds().size());
    }

    @Test
    void testGPUAllocationLocalGpuCountEqualsTotalWhenAllLocal() {
        /**
         * ISSUE: When all GPUs are local, localGpuCount should equal total
         * 
         * EXPECTED: Should be valid and consistent
         */
        
        Set<String> gpuIds = Set.of("GPU-0", "GPU-1", "GPU-2");
        long localGpuCount = 3;  // All 3 are local

        GPUAllocation allocation = new GPUAllocation(gpuIds, localGpuCount);
        
        assertEquals(3, allocation.getLocalGpuCount());
        assertEquals(3, allocation.getGpuIds().size());
        assertEquals(allocation.getGpuIds().size(), allocation.getLocalGpuCount());
    }

    @Test
    void testGPUAllocationConsistencyCheck() {
        /**
         * ISSUE: No consistency check between gpuIds and localGpuCount
         * 
         * SCENARIO: Create allocation and verify internal consistency
         * EXPECTED: localGpuCount <= gpuIds.size() should always hold
         */
        
        // Valid case: partial locality
        Set<String> gpuIds1 = Set.of("GPU-0", "GPU-1", "GPU-2", "GPU-3");
        GPUAllocation allocation1 = new GPUAllocation(gpuIds1, 2);
        
        assertTrue(allocation1.getLocalGpuCount() <= allocation1.getGpuIds().size(),
            "localGpuCount should not exceed total GPU count");

        // Valid case: all local
        GPUAllocation allocation2 = new GPUAllocation(gpuIds1, 4);
        assertEquals(4, allocation2.getLocalGpuCount());
        
        // Valid case: none local
        GPUAllocation allocation3 = new GPUAllocation(gpuIds1, 0);
        assertEquals(0, allocation3.getLocalGpuCount());

        // Test consistency after object creation
        Set<String> allocatedGpus = allocation1.getGpuIds();
        long localCount = allocation1.getLocalGpuCount();
        
        assertTrue(localCount <= allocatedGpus.size(),
            "Consistency: localGpuCount must not exceed number of allocated GPUs");
    }

    @Test
    void testGPUAllocationEmptyGpuIdsWithZeroLocal() {
        /**
         * ISSUE: Empty GPU allocation should have zero local count
         * 
         * EXPECTED: Valid to have empty set with localGpuCount = 0
         */
        
        Set<String> gpuIds = Set.of();
        long localGpuCount = 0;

        GPUAllocation allocation = new GPUAllocation(gpuIds, localGpuCount);
        
        assertTrue(allocation.getGpuIds().isEmpty());
        assertEquals(0, allocation.getLocalGpuCount());
    }

    @Test
    void testGPUAllocationBuilderValidates() {
        /**
         * ISSUE: Builder pattern should also validate inputs
         * 
         * EXPECTED: GPUAllocation builder should enforce same validations
         */
        
        // After fix, builder should also validate:
        /*
        GPUAllocation allocation = GPUAllocation.builder()
            .gpuIds(Set.of("GPU-0", "GPU-1"))
            .localGpuCount(5)  // Invalid
            .build();
        
        assertThrows(IllegalArgumentException.class, () -> allocation);
        */
        
        // For now, test the constructor directly
        assertThrows(IllegalArgumentException.class, () -> {
            new GPUAllocation(Set.of("GPU-0"), 10);
        });
    }

    @Test
    void testGPUAllocationSerializationPreservesValidation() {
        /**
         * ISSUE: After serialization/deserialization, validation should still hold
         * 
         * EXPECTED: Even after JSON round-trip, localGpuCount <= gpuIds.size()
         */
        
        // Create valid allocation
        Set<String> gpuIds = Set.of("GPU-0", "GPU-1", "GPU-2");
        GPUAllocation original = new GPUAllocation(gpuIds, 2);
        
        // Serialize and deserialize (simplified test)
        GPUAllocation deserialized = new GPUAllocation(original.getGpuIds(), 
                                                      original.getLocalGpuCount());
        
        assertEquals(original.getGpuIds(), deserialized.getGpuIds());
        assertEquals(original.getLocalGpuCount(), deserialized.getLocalGpuCount());
        
        // Validity preserved
        assertTrue(deserialized.getLocalGpuCount() <= deserialized.getGpuIds().size());
    }

    @Test
    void testGPUAllocationWithSingleGpu() {
        /**
         * ISSUE: Single GPU allocation edge case
         */
        
        Set<String> gpuIds = Set.of("GPU-0");
        
        // Single GPU, all local
        GPUAllocation allocation1 = new GPUAllocation(gpuIds, 1);
        assertEquals(1, allocation1.getLocalGpuCount());
        
        // Single GPU, none local (cross-NUMA)
        GPUAllocation allocation2 = new GPUAllocation(gpuIds, 0);
        assertEquals(0, allocation2.getLocalGpuCount());
    }

    @Test
    void testGPUAllocationLargeNumberOfGPUs() {
        /**
         * ISSUE: Large GPU set validation
         */
        
        Set<String> gpuIds = IntStream.range(0, 100)
            .mapToObj(i -> "GPU-" + i)
            .collect(Collectors.toSet());
        
        // All 100 GPUs local
        GPUAllocation allocation1 = new GPUAllocation(gpuIds, 100);
        assertEquals(100, allocation1.getLocalGpuCount());
        
        // 50 out of 100 GPUs local
        GPUAllocation allocation2 = new GPUAllocation(gpuIds, 50);
        assertEquals(50, allocation2.getLocalGpuCount());
        
        // 0 out of 100 GPUs local (all cross-NUMA)
        GPUAllocation allocation3 = new GPUAllocation(gpuIds, 0);
        assertEquals(0, allocation3.getLocalGpuCount());
    }
}
