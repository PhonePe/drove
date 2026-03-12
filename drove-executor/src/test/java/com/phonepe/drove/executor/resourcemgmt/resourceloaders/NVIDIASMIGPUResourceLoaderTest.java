package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import lombok.SneakyThrows;
import lombok.val;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import com.phonepe.drove.models.info.GPUInfo;

import static org.junit.jupiter.api.Assertions.*;

class NVIDIASMIGPUResourceLoaderTest {
    @Test
    void testGPUloadOnSystemWithoutNvidiaSMI() {
        val gpus = new NVIDIASMIGPUResourceLoader().loadGPUInfo();
        assertNotNull(gpus);
        assertTrue(gpus.isEmpty());
    }

    @Test
    void testGPUloadOnSystemWithNvidiaSMI() {
        val underTest = new NVIDIASMIGPUResourceLoader() {
            @NotNull
            @SneakyThrows
            @Override
            protected String queryNvidiaSMI() {
                return IOUtils.toString(
                        Objects.requireNonNull(getClass().getResourceAsStream("/gpu-loader/h100.xml")),
                        StandardCharsets.UTF_8);
            }

            @NotNull
            @SneakyThrows
            @Override
            protected String queryNvidiaSMITopo() {
                return IOUtils.toString(
                        Objects.requireNonNull(getClass().getResourceAsStream("/gpu-loader/h100-topo.txt")),
                        StandardCharsets.UTF_8);
            }
        };
        val gpus = underTest.loadGPUInfo();
        assertNotNull(gpus);
        assertEquals(8, gpus.size());
        for (val gpu : gpus) {
            assertNotNull(gpu.getGpuId());
            assertNotNull(gpu.getGpuModel());
            assertEquals("NVIDIA", gpu.getGpuProvider());
            assertNotNull(gpu.getNumaNodeAffinity());
        }
        // GPUs 0-3 are on NUMA 0, GPUs 4-7 are on NUMA 1
        val numaValues = gpus.stream().map(GPUInfo::getNumaNodeAffinity).distinct().sorted().toList();
        assertEquals(2, numaValues.size());
        assertEquals(0, numaValues.get(0));
        assertEquals(1, numaValues.get(1));
    }

    @Test
    void testParseNumaAffinityFromTopoOutput() {
        val loader = new NVIDIASMIGPUResourceLoader();
        val topoOutput = """
                \tGPU0\tGPU1\tGPU2\tGPU3\tCPU Affinity\tNUMA Affinity
                GPU0\t X \tNV18\tNV18\tNV18\t0-15\t0
                GPU1\tNV18\t X \tNV18\tNV18\t0-15\t0
                GPU2\tNV18\tNV18\t X \tNV18\t16-31\t1
                GPU3\tNV18\tNV18\tNV18\t X \t16-31\t1

                Legend:
                  X    = Self
                """;
        val result = loader.parseNumaAffinityFromTopoOutput(topoOutput);
        assertEquals(Map.of(0, 0, 1, 0, 2, 1, 3, 1), result);
    }

    @Test
    void testParseNumaAffinityFromTopoOutputWithNAValue() {
        val loader = new NVIDIASMIGPUResourceLoader();
        val topoOutput = """
                \tGPU0\tGPU1\tCPU Affinity\tNUMA Affinity
                GPU0\t X \tPHB\t0-15\tN/A
                GPU1\tPHB\t X \t0-15\tN/A

                Legend:
                  X    = Self
                """;
        // N/A values should be skipped gracefully; result should be empty
        val result = loader.parseNumaAffinityFromTopoOutput(topoOutput);
        assertTrue(result.isEmpty());
    }

    @Test
    void testParseNumaAffinityFromTopoOutputMalformed() {
        val loader = new NVIDIASMIGPUResourceLoader();
        // No header line containing "NUMA Affinity" — completely malformed
        val topoOutput = """
                some random garbage
                GPU0  X   PHB
                GPU1  PHB  X
                """;
        val result = loader.parseNumaAffinityFromTopoOutput(topoOutput);
        assertTrue(result.isEmpty());
    }

    @Test
    void testParseNumaAffinityFromTopoOutputHeaderPresentButRowsMalformed() {
        val loader = new NVIDIASMIGPUResourceLoader();
        // Header present but GPU rows have no parseable last column
        val topoOutput = """
                \tGPU0\tGPU1\tCPU Affinity\tNUMA Affinity
                GPU0
                GPU1
                """;
        // rows don't match "GPU\\d+\\s+.*" — should be skipped, result empty
        val result = loader.parseNumaAffinityFromTopoOutput(topoOutput);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGPULoadFallsBackToNullNumaWhenTopoCommandFails() {
        val underTest = new NVIDIASMIGPUResourceLoader() {
            @NotNull
            @SneakyThrows
            @Override
            protected String queryNvidiaSMI() {
                return IOUtils.toString(
                        Objects.requireNonNull(getClass().getResourceAsStream("/gpu-loader/h100.xml")),
                        StandardCharsets.UTF_8);
            }

            @NotNull
            @Override
            protected String queryNvidiaSMITopo() throws IOException {
                throw new IOException("nvidia-smi topo: command not found");
            }
        };
        val gpus = underTest.loadGPUInfo();
        assertNotNull(gpus);
        assertEquals(8, gpus.size());
        // NUMA affinity must be null for every GPU since topo command failed
        for (val gpu : gpus) {
            assertNull(gpu.getNumaNodeAffinity());
        }
    }
}