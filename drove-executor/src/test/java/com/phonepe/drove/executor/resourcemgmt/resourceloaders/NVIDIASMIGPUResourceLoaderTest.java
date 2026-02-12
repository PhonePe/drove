package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import lombok.SneakyThrows;
import lombok.val;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

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
        // This test assumes that the system running the test has nvidia-smi installed and accessible.
        val underTest = new NVIDIASMIGPUResourceLoader() {
            @NotNull
            @SneakyThrows
            @Override
            protected String queryNvidiaSMI() {
                return IOUtils.toString(this.getClass().getResourceAsStream("/gpu-loader/h100.xml"),"UTF-8");
            }
        };
        val gpus = underTest.loadGPUInfo();
        assertNotNull(gpus);
        assertEquals(gpus.size(), 8);
        // We can't assert on the number of GPUs or their properties, but we can check that they are valid.
        for (val gpu : gpus) {
            assertNotNull(gpu.getGpuId());
            assertNotNull(gpu.getGpuModel());
            assertEquals(gpu.getGpuProvider(), "NVIDIA");
        }
    }
}