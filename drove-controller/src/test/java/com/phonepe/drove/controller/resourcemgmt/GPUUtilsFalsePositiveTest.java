package com.phonepe.drove.controller.resourcemgmt;

import com.phonepe.drove.models.application.requirements.GpuType;
import com.phonepe.drove.models.application.requirements.GpuVendor;
import com.phonepe.drove.models.info.GPUInfo;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for GpuTypeMatcher Substring Bug.
 * 
 * ISSUE: GpuTypeMatcher uses substring matching which causes false positives
 * 
 * Current State: Uses String.contains() for matching, causing:
 *   - "100" in "H100" matches "H200" (incorrect)
 *   - "MI300X" matches "MI300X-PCIe" without exact match (incorrect)
 * Expected State: Should use token-based matching with proper boundaries
 * 
 * Rationale: Substring matching is too loose and causes:
 * - Incorrect GPU allocations
 * - Users getting wrong GPU types
 * - Difficult debugging of "why did I get this GPU?" issues
 */
class GPUUtilsFalsePositiveTest {

    @Test
    void testH100ShouldNotMatchH200() {
        /**
         * ISSUE: Substring "100" in "H100" incorrectly matches "H200"
         * 
         * Current behavior: "H200".contains("100") = true (BUG)
         * Expected: Should NOT match because H200 ≠ H100
         */
        
        val gpuH200 = new GPUInfo("GPU-0", GpuVendor.NVIDIA, "H200", 0, true);
        val requestedH100 = new GpuType(GpuVendor.NVIDIA, "H100");

        boolean matches = GPUUtils.matches(gpuH200, requestedH100);
        
        // After fix: should be false (H200 should NOT match H100)
        // Current: may be true due to substring match on "100"
        assertFalse(matches, 
            "H200 should NOT match H100 request. " +
            "Current implementation may incorrectly match due to '100' substring");
    }

    @Test
    void testH100ShouldNotMatchA10() {
        /**
         * ISSUE: Substring "10" causes false matches
         * 
         * "A10" contains "10" (from "H100")
         */
        
        val gpuA10 = new GPUInfo("GPU-0", GpuVendor.NVIDIA, "A10", 0, true);
        val requestedH100 = new GpuType(GpuVendor.NVIDIA, "H100");

        boolean matches = GPUUtils.matches(gpuA10, requestedH100);
        
        // After fix: should be false
        assertFalse(matches, "A10 should NOT match H100 request");
    }

    @Test
    void testH100ShouldNotMatchH10() {
        /**
         * ISSUE: Substring "H10" causes false match
         * 
         * "H10" contains "H10" (from "H100")
         */
        
        val gpuH10 = new GPUInfo("GPU-0", GpuVendor.NVIDIA, "H10", 0, true);
        val requestedH100 = new GpuType(GpuVendor.NVIDIA, "H100");

        boolean matches = GPUUtils.matches(gpuH10, requestedH100);
        
        // After fix: should be false (H10 ≠ H100)
        assertFalse(matches, "H10 should NOT match H100 request");
    }

    @Test
    void testMI300XShouldNotMatchMI300XPCIeWithoutExplicitMatch() {
        /**
         * ISSUE: "MI300X" substring matches "MI300X-PCIe" even when exact match not requested
         * 
         * SCENARIO: User requests "MI300X" - should it match "MI300X-PCIe"?
         * 
         * Current behavior: contains("MI300X") = true for "MI300X-PCIe" (may be too permissive)
         * Expected: Should have option for exact match vs. prefix match
         */
        
        val gpuMI300XPCIe = new GPUInfo("GPU-0", GpuVendor.AMD, "MI300X-PCIe", 0, true);
        val requestedMI300X = new GpuType(GpuVendor.AMD, "MI300X");

        boolean matches = GPUUtils.matches(gpuMI300XPCIe, requestedMI300X);
        
        // After fix: This should probably be false for exact match, or we need
        // an explicit configuration for prefix matching
        // The current implementation will likely match due to substring
        // Consider: should add explicit exact match option
        assertFalse(matches, 
            "MI300X request should not automatically match MI300X-PCIe unless prefix matching is explicitly enabled");
    }

    @Test
    void testExactModelNamesShouldNotOverlap() {
        /**
         * ISSUE: Similar model names create false positives
         * 
         * Test a comprehensive set of GPU name pairs that should NOT match
         */
        
        val testCases = new Object[][] {
            {"NVIDIA H100 80GB HBM3", "NVIDIA H200 141GB HBM3", false},  // H100 ≠ H200
            {"NVIDIA A10", "NVIDIA A100", false},  // A10 ≠ A100
            {"NVIDIA L40", "NVIDIA L40S", false},  // L40 ≠ L40S
            {"AMD MI250", "AMD MI300X", false},  // Different generation
            {"NVIDIA V100", "NVIDIA GV100", false},  // V100 ≠ GV100
            {"NVIDIA RTX 4090", "NVIDIA RTX 4080", false},  // 4090 ≠ 4080
            {"NVIDIA GeForce GTX 1080", "NVIDIA GeForce GTX 1080 Ti", false},  // 1080 ≠ 1080 Ti
        };

        for (Object[] testCase : testCases) {
            String gpuModel = (String) testCase[0];
            String requestedType = (String) testCase[1];
            boolean shouldMatch = (boolean) testCase[2];

            val gpu = new GPUInfo("GPU-0", GpuVendor.NVIDIA, gpuModel, 0, true);
            val requested = new GpuType(GpuVendor.NVIDIA, requestedType);

            boolean matches = GPUUtils.matches(gpu, requested);

            // All test cases should NOT match
            assertFalse(matches, 
                String.format("GPU model '%s' should NOT match requested type '%s'", gpuModel, requestedType));
        }
    }

    @Test
    void testNumericSuffixesMustMatchExactly() {
        /**
         * ISSUE: Numeric suffixes are not being compared properly
         * 
         * SCENARIO: "H100" vs "H100S" vs "H100XM" - should be distinct GPUs
         */
        
        val gpuH100 = new GPUInfo("GPU-0", GpuVendor.NVIDIA, "H100", 0, true);
        val gpuH100S = new GPUInfo("GPU-1", GpuVendor.NVIDIA, "H100S", 0, true);
        val gpuH100XM = new GPUInfo("GPU-2", GpuVendor.NVIDIA, "H100XM", 0, true);

        val requestedH100 = new GpuType(GpuVendor.NVIDIA, "H100");

        // H100 should match H100
        assertTrue(GPUUtils.matches(gpuH100, requestedH100),
            "H100 should match H100");

        // H100S should NOT match H100 (different GPU)
        assertFalse(GPUUtils.matches(gpuH100S, requestedH100),
            "H100S should NOT match H100 request");

        // H100XM should NOT match H100 (different GPU)
        assertFalse(GPUUtils.matches(gpuH100XM, requestedH100),
            "H100XM should NOT match H100 request");
    }

    @Test
    void testVendorAndModelMustBothMatch() {
        /**
         * ISSUE: Currently checks vendor first, but model matching is too loose
         * 
         * SCENARIO: NVIDIA H100 vs AMD H100 - vendor check should prevent match
         * This test verifies that vendor mismatch correctly prevents matching
         */
        
        val gpuNVIDIAH100 = new GPUInfo("GPU-0", GpuVendor.NVIDIA, "H100", 0, true);
        val gpuAMDH100 = new GPUInfo("GPU-1", GpuVendor.AMD, "H100", 0, true);  // Hypothetical AMD H100

        val requestedNVIDIAH100 = new GpuType(GpuVendor.NVIDIA, "H100");

        // NVIDIA H100 should match NVIDIA H100 request
        assertTrue(GPUUtils.matches(gpuNVIDIAH100, requestedNVIDIAH100));

        // AMD H100 should NOT match NVIDIA H100 request (vendor mismatch)
        assertFalse(GPUUtils.matches(gpuAMDH100, requestedNVIDIAH100),
            "AMD H100 should NOT match NVIDIA H100 request (vendor mismatch)");
    }

    @Test
    void testGpuTypeMatcherUsesTokenBasedMatchingNotSubstring() {
        /**
         * ISSUE: Implementation uses String.contains() which is too permissive
         * 
         * EXPECTED: Should tokenize the strings and match on tokens, not substrings
         * 
         * Example:
         * - "H100 80GB" → tokens: ["h100", "80gb"]
         * - "H200 141GB" → tokens: ["h200", "141gb"]
         * - No common tokens → no match
         */
        
        val gpu = new GPUInfo("GPU-0", GpuVendor.NVIDIA, "H200 141GB HBM3", 0, true);
        val requested = new GpuType(GpuVendor.NVIDIA, "H100 80GB HBM3");

        boolean matches = GPUUtils.matches(gpu, requested);
        
        // After fix: should be false (different token sets)
        assertFalse(matches, 
            "H200 141GB should NOT match H100 80GB even though both contain '80gb' in similar form");
    }
}
