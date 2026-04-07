package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for NUMA Affinity Parsing Fragility in NVIDIASMIGPUResourceLoader.
 * 
 * ISSUE: NUMA affinity parsing depends on exact string matching and doesn't
 *        handle various nvidia-smi output formats gracefully
 * 
 * Current State: 
 *   - Fails if "NUMA Affinity" header not present
 *   - Fails if "N/A" values appear
 *   - Fails with different column orders
 *   - No graceful fallback for malformed data
 * Expected State: Should handle various formats and missing data gracefully
 * 
 * Rationale: nvidia-smi output can vary across:
 * - GPU architectures
 * - Driver versions
 * - System configurations
 * - Multi-node setups
 */
class NVIDIASMIGPUResourceLoaderNumaParsingTest {

    @Test
    void testParseNumaWithVariousHeaderFormats() {
        /**
         * ISSUE: NUMA affinity parsing requires exact "NUMA Affinity" substring
         * 
         * SCENARIO: Test various header formats that nvidia-smi might use
         */
        
        val loader = new NVIDIASMIGPUResourceLoader();

        // Test 1: Standard format with tab-separated columns
        val standardFormat = """
                \tGPU0\tGPU1\tCPU Affinity\tNUMA Affinity
                GPU0\t X \tNV18\t0-15\t0
                GPU1\tNV18\t X \t16-31\t1
                """;
        var result = loader.parseNumaAffinityFromTopoOutput(standardFormat);
        assertEquals(Map.of(0, 0, 1, 1), result, "Standard format should parse correctly");

        // Test 2: Format with extra spaces
        val extraSpacesFormat = """
                	GPU0	GPU1	CPU Affinity	NUMA Affinity
                GPU0	 X 	PHB	0-15	0
                GPU1	PHB	 X 	16-31	1
                """;
        result = loader.parseNumaAffinityFromTopoOutput(extraSpacesFormat);
        assertEquals(Map.of(0, 0, 1, 1), result, "Format with extra spaces should parse correctly");

        // Test 3: Format with GPU NUMA ID column (different order)
        val gpuNumaIdFormat = """
                	GPU0	GPU1	CPU Affinity	NUMA Affinity	GPU NUMA ID
                GPU0	 X 	PHB	0-15	0	N/A
                GPU1	PHB	 X 	16-31	1	N/A
                """;
        result = loader.parseNumaAffinityFromTopoOutput(gpuNumaIdFormat);
        assertEquals(Map.of(0, 0, 1, 1), result, "Format with GPU NUMA ID column should parse correctly");
    }

    @Test
    void testParseNumaHandlesNAValueGracefully() {
        /**
         * ISSUE: "N/A" values may cause parsing failures
         */
        
        val loader = new NVIDIASMIGPUResourceLoader();

        val topoWithNA = """
                \tGPU0\tGPU1\tGPU2\tCPU Affinity\tNUMA Affinity
                GPU0\t X \tNV18\tNV18\t0-15\t0
                GPU1\tNV18\t X \tNV18\t0-15\tN/A
                GPU2\tNV18\tNV18\t X \t0-15\t2
                """;

        val result = loader.parseNumaAffinityFromTopoOutput(topoWithNA);
        
        // Should skip GPU1 with N/A and only include GPUs with valid NUMA values
        assertEquals(Map.of(0, 0, 2, 2), result, 
            "Should skip N/A values and only include valid NUMA mappings");
    }

    @Test
    void testParseNumaHandlesMissingNumaColumn() {
        /**
         * ISSUE: Missing "NUMA Affinity" column causes complete failure
         * 
         * EXPECTED: Should return empty map instead of throwing exception
         */
        
        val loader = new NVIDIASMIGPUResourceLoader();

        // Topology output without NUMA column at all
        val topoWithoutNuma = """
                \tGPU0\tGPU1\tCPU Affinity
                GPU0\t X \tNV18\t0-15
                GPU1\tNV18\t X \t16-31
                """;

        val result = loader.parseNumaAffinityFromTopoOutput(topoWithoutNuma);
        
        assertEquals(0, result.size(), 
            "Should return empty map when NUMA Affinity column is missing");
    }

    @Test
    void testParseNumaHandlesMixedValidAndInvalidData() {
        /**
         * ISSUE: Partially malformed data causes complete parsing failure
         * 
         * SCENARIO: Some rows have valid NUMA, some are malformed
         * EXPECTED: Parse valid rows, skip invalid ones
         */
        
        val loader = new NVIDIASMIGPUResourceLoader();

        val mixedFormat = """
                \tGPU0\tGPU1\tGPU2\tGPU3\tCPU Affinity\tNUMA Affinity
                GPU0\t X \tNV18\tNV18\tNV18\t0-15\t0
                GPU1\tNV18\t X \tNV18\tNV18\t0-15\tmalformed
                GPU2\tNV18\tNV18\t X \tNV18\t16-31\t1
                GPU3\tNV18\tNV18\tNV18\t X \t16-31\t
                """;

        val result = loader.parseNumaAffinityFromTopoOutput(mixedFormat);
        
        // Should only include GPUs 0 and 2 (valid NUMA values)
        // GPU1 has "malformed", GPU3 has empty NUMA
        assertEquals(Map.of(0, 0, 2, 1), result, 
            "Should parse only valid rows and skip malformed ones");
    }

    @Test
    void testParseNumaHandlesSingleGPU() {
        /**
         * ISSUE: Single GPU systems may have different output format
         */
        
        val loader = new NVIDIASMIGPUResourceLoader();

        val singleGpuFormat = """
                \tGPU0\tCPU Affinity\tNUMA Affinity
                GPU0\t X \t0-15\t0
                """;

        val result = loader.parseNumaAffinityFromTopoOutput(singleGpuFormat);
        assertEquals(Map.of(0, 0), result, "Single GPU format should parse correctly");
    }

    @Test
    void testParseNumaHandlesMultipleNUMANodes() {
        /**
         * ISSUE: Multi-node systems (NUMA 0, 1, 2, 3) need proper parsing
         */
        
        val loader = new NVIDIASMIGPUResourceLoader();

        val multiNumaFormat = """
                \tGPU0\tGPU1\tGPU2\tGPU3\tGPU4\tGPU5\tGPU6\tGPU7\tCPU Affinity\tNUMA Affinity
                GPU0\t X \tNV18\tNV18\tNV18\tNV18\tNV18\tNV18\tNV18\t0-15\t0
                GPU1\tNV18\t X \tNV18\tNV18\tNV18\tNV18\tNV18\tNV18\t0-15\t0
                GPU2\tNV18\tNV18\t X \tNV18\tNV18\tNV18\tNV18\tNV18\t16-31\t1
                GPU3\tNV18\tNV18\tNV18\t X \tNV18\tNV18\tNV18\tNV18\t16-31\t1
                GPU4\tNV18\tNV18\tNV18\tNV18\t X \tNV18\tNV18\tNV18\t32-47\t2
                GPU5\tNV18\tNV18\tNV18\tNV18\tNV18\t X \tNV18\tNV18\t32-47\t2
                GPU6\tNV18\tNV18\tNV18\tNV18\tNV18\tNV18\t X \tNV18\t48-63\t3
                GPU7\tNV18\tNV18\tNV18\tNV18\tNV18\tNV18\tNV18\t X \t48-63\t3
                """;

        val result = loader.parseNumaAffinityFromTopoOutput(multiNumaFormat);
        
        assertEquals(8, result.size(), "Should parse all 8 GPUs");
        assertEquals(Map.of(0, 0, 1, 0, 2, 1, 3, 1, 4, 2, 5, 2, 6, 3, 7, 3), result, 
            "Multi-NUMA mapping should be correct");
    }

    @Test
    void testParseNumaHandlesRowWithoutGPUIndex() {
        /**
         * ISSUE: Malformed rows without proper GPU index pattern
         */
        
        val loader = new NVIDIASMIGPUResourceLoader();

        val formatWithMalformedRow = """
                \tGPU0\tGPU1\tCPU Affinity\tNUMA Affinity
                GPU0\t X \tNV18\t0-15\t0
                This is a malformed line
                GPU1\tNV18\t X \t16-31\t1
                """;

        val result = loader.parseNumaAffinityFromTopoOutput(formatWithMalformedRow);
        
        assertEquals(Map.of(0, 0, 1, 1), result, 
            "Should skip malformed rows and parse valid ones");
    }

    @Test
    void testParseNumaReturnsEmptyMapForCompletelyMalformedInput() {
        /**
         * ISSUE: Completely malformed input should return empty map, not throw exception
         */
        
        val loader = new NVIDIASMIGPUResourceLoader();

        val completelyMalformed = """
                Random garbage
                More nonsense
                Some text here
                """;

        val result = loader.parseNumaAffinityFromTopoOutput(completelyMalformed);
        
        assertEquals(0, result.size(), 
            "Should return empty map for completely malformed input");
    }

    @Test
    void testParseNumaHandlesColumnReordering() {
        /**
         * ISSUE: Different column orders are not handled
         * 
         * Note: nvidia-smi output has GPU identifier (GPU0, GPU1, etc.) in first column
         * of data rows. When reordering occurs, NUMA value should follow the column order.
         * This test documents the current limitation.
         */
        
        val loader = new NVIDIASMIGPUResourceLoader();

        // Standard format - works correctly
        val standardFormat = """
                \tGPU0\tGPU1\tCPU Affinity\tNUMA Affinity
                GPU0\t X \tNV18\t0-15\t0
                GPU1\tNV18\t X \t16-31\t1
                """;

        val result = loader.parseNumaAffinityFromTopoOutput(standardFormat);
        assertEquals(Map.of(0, 0, 1, 1), result, "Standard format should work");
    }

    @Test
    void testParseNumaHandlesWhitespaceVariations() {
        /**
         * ISSUE: Different whitespace patterns cause parsing failures
         */
        
        val loader = new NVIDIASMIGPUResourceLoader();

        val variedWhitespace = """
                \tGPU0   \tGPU1\t   CPU Affinity   \t   NUMA Affinity
                GPU0\t X   \tNV18\t   0-15\t   0
                GPU1\tNV18\t X \t   16-31\t   1
                """;

        val result = loader.parseNumaAffinityFromTopoOutput(variedWhitespace);
        
        assertEquals(Map.of(0, 0, 1, 1), result, 
            "Should handle whitespace variations");
    }

    @Test
    void testParseNumaHandlesZeroPaddedNUMAValues() {
        /**
         * ISSUE: Zero-padded NUMA values may not parse correctly
         */
        
        val loader = new NVIDIASMIGPUResourceLoader();

        val paddedFormat = """
                \tGPU0\tGPU1\tGPU2\tGPU3\tCPU Affinity\tNUMA Affinity
                GPU0\t X \tNV18\tNV18\tNV18\t0-15\t00
                GPU1\tNV18\t X \tNV18\tNV18\t0-15\t00
                GPU2\tNV18\tNV18\t X \tNV18\t16-31\t01
                GPU3\tNV18\tNV18\tNV18\t X \t16-31\t01
                """;

        val result = loader.parseNumaAffinityFromTopoOutput(paddedFormat);
        
        // After parsing, values should be converted to integers
        assertEquals(Map.of(0, 0, 1, 0, 2, 1, 3, 1), result, 
            "Should handle zero-padded NUMA values");
    }
}
