package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import com.google.common.collect.Sets;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.phonepe.drove.common.CommonTestUtils.set;
import static com.phonepe.drove.executor.ExecutorTestingUtils.resourceConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
@Slf4j
class NumaCtlBasedResourceLoaderTest extends AbstractTestBase {

    private static NumaCtlBasedResourceLoader getNewNumaRL(
            ResourceConfig resourceConfig,
            List<String> numaResponseLines
    ) {
        return new NumaCtlBasedResourceLoader(resourceConfig) {
            @Override
            protected List<String> fetchSystemResourceUsingNumaCTL() throws Exception {
                return numaResponseLines;
            }
        };
    }

    @SneakyThrows
    @Test
    void testBasicParsing() {
        val rl = getNewNumaRL(resourceConfig(), readLinesFromFile("/numactl-resource-loader-test/dualnode.txt"));
        val info = rl.loadSystemResources();
        assertFalse(info.isEmpty());
        assertEquals(2, info.size());
        assertTrue(Sets.difference(Set.of(4, 6, 8, 10, 12, 14, 16, 18, 2), info.get(0).getAvailableCores()).isEmpty());
        assertEquals(172992L, info.get(0).getMemoryInMB());
        assertTrue(Sets.difference(Set.of(3, 5, 7, 9, 11, 13, 15, 17, 19), info.get(1).getAvailableCores()).isEmpty());
        assertEquals(174158L, info.get(1).getMemoryInMB());
    }

    @SneakyThrows
    @Test
    void testNodeNoData() {
        val rl = getNewNumaRL(resourceConfig(), readLinesFromFile("/numactl-resource-loader-test/nodata.txt"));
        val info = rl.loadSystemResources();
        assertTrue(info.isEmpty());
    }

    @SneakyThrows
    @Test
    void testInvalidNdoeData() {
        val rl = getNewNumaRL(resourceConfig(), readLinesFromFile("/numactl-resource-loader-test/no-cores.txt"));
        val info = rl.loadSystemResources();
        assertFalse(info.isEmpty());
        assertEquals(1, info.size());
        assertTrue(info.get(0).getAvailableCores().isEmpty());
    }

    @SneakyThrows
    @Test
    void testNegativeNdoeData() {
        val rl = getNewNumaRL(resourceConfig(), readLinesFromFile("/numactl-resource-loader-test/invalidnode.txt"));

        val info = rl.loadSystemResources();
        assertTrue(info.isEmpty());
    }

    @SneakyThrows
    @Test
    void testAllCoresReserved() {
        val resourceConfig = new ResourceConfig();
        resourceConfig.setOsCores(set(19))
                .setExposedMemPercentage(90)
                .setTags(Set.of("test-machine"));
        val rl = getNewNumaRL(resourceConfig, readLinesFromFile("/numactl-resource-loader-test/dualnode.txt"));

        val info = rl.loadSystemResources();
        assertFalse(info.isEmpty());
        assertEquals(2, info.size());
        assertTrue(info.get(0).getAvailableCores().isEmpty());
        assertTrue(info.get(1).getAvailableCores().isEmpty());
    }

    @SneakyThrows
    @Test
    void testMemMismatch() {
        val resourceConfig = new ResourceConfig()
                .setOsCores(IntStream.rangeClosed(0, 19).boxed().collect(Collectors.toUnmodifiableSet()))
                .setExposedMemPercentage(90)
                .setTags(Set.of("test-machine"));
        val rl = getNewNumaRL(resourceConfig, readLinesFromFile("/numactl-resource-loader-test/mismatch.txt"));
        try {
            rl.loadSystemResources();
            fail("Should have thrown exception");
        } catch (IllegalStateException e) {
            assertEquals("Mismatch between memory nodes and cores", e.getMessage());
        }
    }

    @Test
    @SneakyThrows
    void testNumaCtlCommandLoader() {
        val nc = new File("/usr/bin/numactl");
        if (nc.exists() && !nc.isDirectory() && nc.canExecute()) {
            val rl = new NumaCtlBasedResourceLoader(resourceConfig());
            val info = rl.loadSystemResources();
            assertFalse(info.isEmpty());
            assertFalse(info.get(0).getAvailableCores().isEmpty());
            assertNotEquals(0, info.get(0).getMemoryInMB());
        } else {
            log.warn("Numactl does not exist on this machine and hence test is skipped");
        }
    }
}