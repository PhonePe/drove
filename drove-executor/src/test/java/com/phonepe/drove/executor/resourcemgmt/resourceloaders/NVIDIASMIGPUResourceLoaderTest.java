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
import java.util.stream.Collectors;

import com.phonepe.drove.models.application.requirements.GpuVendor;
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
    void testNvidiaSMICommandNotFoundReturnsEmptyGPUList() throws IOException, InterruptedException {
        val underTest = new NVIDIASMIGPUResourceLoader() {
            @NotNull
            @Override
            protected String queryNvidiaSMI() throws InterruptedException, IOException {
                // Simulate error=2 (ENOENT - No such file or directory)
                throw new IOException("Cannot run program \"nvidia-smi\": error=2, No such file or directory");
            }
        };
        val gpus = underTest.loadGPUInfo();
        assertNotNull(gpus);
        assertTrue(gpus.isEmpty(), "Should return empty GPU list when nvidia-smi is not available");
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
            assertTrue(gpu.getGpuId().startsWith("GPU-"));
            assertNotNull(gpu.getGpuModel());
            assertEquals(GpuVendor.NVIDIA, gpu.getGpuVendor());
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

    @Test
    void testGPULoadMapsNumaByMinorNumberNotXmlOrder() {
        val underTest = new NVIDIASMIGPUResourceLoader() {
            @NotNull
            @Override
            protected String queryNvidiaSMI() {
                return """
                        <nvidia_smi_log>
                          <gpu id=\"00000000:0A:00.0\">
                            <product_name>H100</product_name>
                            <product_brand>NVIDIA</product_brand>
                            <uuid>GPU-SECOND</uuid>
                            <minor_number>1</minor_number>
                          </gpu>
                          <gpu id=\"00000000:0B:00.0\">
                            <product_name>H100</product_name>
                            <product_brand>NVIDIA</product_brand>
                            <uuid>GPU-FIRST</uuid>
                            <minor_number>0</minor_number>
                          </gpu>
                        </nvidia_smi_log>
                        """;
            }

            @NotNull
            @Override
            protected String queryNvidiaSMITopo() {
                return """
                        GPU0\tGPU1\tCPU Affinity\tNUMA Affinity
                        GPU0\t X \tNV18\t0-15\t0
                        GPU1\tNV18\t X \t16-31\t1
                        """;
            }
        };

        val gpusById = underTest.loadGPUInfo().stream()
                .collect(Collectors.toUnmodifiableMap(GPUInfo::getGpuId, gpu -> gpu));
        assertEquals(2, gpusById.size());
        assertEquals(0, gpusById.get("GPU-FIRST").getNumaNodeAffinity());
        assertEquals(1, gpusById.get("GPU-SECOND").getNumaNodeAffinity());
    }

    @Test
    void testParseNumaAffinityFromTopoOutputWithGpuNumaIdColumn() {
        val loader = new NVIDIASMIGPUResourceLoader();
        val topoOutput = """
                	GPU0	GPU1	CPU Affinity	NUMA Affinity	GPU NUMA ID
                GPU0	 X 	PHB	0-15	0	N/A
                GPU1	PHB	 X 	16-31	1	N/A

                Legend:
                  X    = Self
                """;
        val result = loader.parseNumaAffinityFromTopoOutput(topoOutput);
        assertEquals(Map.of(0, 0, 1, 1), result);
    }

    @Test
    void testGPULoadRecognizesMigDevicesWhenMigEnabled() {
        val underTest = new NVIDIASMIGPUResourceLoader() {
            @NotNull
            @Override
            protected String queryNvidiaSMI() {
                return """
                        <nvidia_smi_log>
                          <gpu id=\"00000000:01:00.0\">
                            <product_name>NVIDIA H100 80GB HBM3</product_name>
                            <product_brand>NVIDIA</product_brand>
                            <mig_mode>
                              <current_mig>Enabled</current_mig>
                              <pending_mig>Enabled</pending_mig>
                            </mig_mode>
                            <uuid>GPU-PARENT</uuid>
                            <minor_number>0</minor_number>
                            <mig_devices>
                              <mig_device>
                                <index>0</index>
                                <uuid>MIG-1111</uuid>
                              </mig_device>
                              <mig_device>
                                <index>1</index>
                                <uuid>MIG-2222</uuid>
                              </mig_device>
                            </mig_devices>
                          </gpu>
                        </nvidia_smi_log>
                        """;
            }

            @NotNull
            @Override
            protected String queryNvidiaSMITopo() {
                // Single-GPU header: tokens = [GPU0, CPU, Affinity, NUMA, Affinity]
                // "NUMA" is at index 3. Data row tokens = [GPU0, X, 0-15, 0] -> index 3 = "0"
                return "\tGPU0\tCPU Affinity\tNUMA Affinity\n"
                        + "GPU0\t X \t0-15\t0\n";
            }
        };

        val gpusById = underTest.loadGPUInfo().stream()
                .collect(Collectors.toUnmodifiableMap(GPUInfo::getGpuId, gpu -> gpu));
        assertEquals(2, gpusById.size());
        assertTrue(gpusById.containsKey("MIG-1111"));
        assertTrue(gpusById.containsKey("MIG-2222"));
        assertFalse(gpusById.containsKey("GPU-PARENT"));
        assertTrue(gpusById.get("MIG-1111").getGpuModel().toLowerCase().contains("mig"));
        assertEquals(0, gpusById.get("MIG-1111").getNumaNodeAffinity());
    }
}