package com.phonepe.drove.executor.resourcemgmt;

import com.google.common.collect.Sets;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class NumaCtlBasedResourceLoaderTest {

    @SneakyThrows
    @Test
    void testBasicParsing() {
        val resourceConfig = new ResourceConfig();
        resourceConfig.setOsCores(Set.of(0, 1));
        resourceConfig.setExposedMemPercentage(90);
        resourceConfig.setTags(Set.of("test-machine"));
        val rl = new NumaCtlBasedResourceLoader(resourceConfig);

        val info = rl.parseCommandOutput(
                readLinesFromFile("/numactl-resource-loader-test/dualnode.txt"));
        assertFalse(info.isEmpty());
        assertEquals(2, info.size());
        assertTrue(Sets.difference(Set.of(4, 6, 8, 10, 12, 14, 16, 18, 2), info.get(0).getAvailableCores()).isEmpty());
        assertEquals(172992L, info.get(0).getMemoryInMB());
        assertTrue(Sets.difference(Set.of(3, 5, 7, 9, 11, 13, 15, 17, 19), info.get(1).getAvailableCores()).isEmpty());
        assertEquals(174158L, info.get(1).getMemoryInMB());
    }

    private List<String> readLinesFromFile(String fileName) throws IOException, URISyntaxException {
        return Files.readAllLines(Paths.get(getClass().getResource(fileName)
                                                    .toURI()));
    }

    @SneakyThrows
    @Test
    void testNodeNoData() {
        val resourceConfig = new ResourceConfig();
        resourceConfig.setOsCores(Set.of(0, 1));
        resourceConfig.setExposedMemPercentage(90);
        resourceConfig.setTags(Set.of("test-machine"));
        val rl = new NumaCtlBasedResourceLoader(resourceConfig);

        val info = rl.parseCommandOutput(readLinesFromFile(
                "/numactl-resource-loader-test/nodata.txt"));
        assertTrue(info.isEmpty());
    }


    @SneakyThrows
    @Test
    void testInvalidNdoeData() {
        val resourceConfig = new ResourceConfig();
        resourceConfig.setOsCores(Set.of(0, 1));
        resourceConfig.setExposedMemPercentage(90);
        resourceConfig.setTags(Set.of("test-machine"));
        val rl = new NumaCtlBasedResourceLoader(resourceConfig);

        val info = rl.parseCommandOutput(readLinesFromFile(
                "/numactl-resource-loader-test/no-cores.txt"));
        assertFalse(info.isEmpty());
        assertEquals(1, info.size());
        assertTrue(info.get(0).getAvailableCores().isEmpty());
    }

    @SneakyThrows
    @Test
    void testNegativeNdoeData() {
        val resourceConfig = new ResourceConfig();
        resourceConfig.setOsCores(Set.of(0, 1));
        resourceConfig.setExposedMemPercentage(90);
        resourceConfig.setTags(Set.of("test-machine"));
        val rl = new NumaCtlBasedResourceLoader(resourceConfig);

        val info = rl.parseCommandOutput(readLinesFromFile(
                "/numactl-resource-loader-test/invalidnode.txt"));
        assertTrue(info.isEmpty());
    }

    @SneakyThrows
    @Test
    void testAllCoresReserved() {
        val resourceConfig = new ResourceConfig();
        resourceConfig.setOsCores(IntStream.rangeClosed(0, 19).boxed().collect(Collectors.toUnmodifiableSet()));
        resourceConfig.setExposedMemPercentage(90);
        resourceConfig.setTags(Set.of("test-machine"));
        val rl = new NumaCtlBasedResourceLoader(resourceConfig);

        val info = rl.parseCommandOutput(
                readLinesFromFile("/numactl-resource-loader-test/dualnode.txt"));
        assertFalse(info.isEmpty());
        assertEquals(2, info.size());
        assertTrue(info.get(0).getAvailableCores().isEmpty());
        assertTrue(info.get(1).getAvailableCores().isEmpty());
    }

    @SneakyThrows
    @Test
    void testMemMismatch() {
        val resourceConfig = new ResourceConfig();
        resourceConfig.setOsCores(IntStream.rangeClosed(0, 19).boxed().collect(Collectors.toUnmodifiableSet()));
        resourceConfig.setExposedMemPercentage(90);
        resourceConfig.setTags(Set.of("test-machine"));
        val rl = new NumaCtlBasedResourceLoader(resourceConfig);

        val lines = readLinesFromFile("/numactl-resource-loader-test/mismatch.txt");
        try {
            rl.parseCommandOutput(lines);
            fail("Should have thrown exception");
        }
        catch (IllegalStateException e) {
            assertEquals("Mismatch between memory nodes and cores", e.getMessage());
        }
    }
}