package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.models.application.requirements.GpuVendor;
import com.phonepe.drove.models.info.GPUInfo;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

class ExecutorResourceLoaderImplTest {

    @Test
    void testBothLoadersAlwaysCalled() {
        val numaResourceLoader = Mockito.mock(NumaResourceLoader.class);
        val gpuResourceLoader = Mockito.mock(GPUResourceLoader.class);
        Mockito.when(numaResourceLoader.loadSystemResources()).thenReturn(Map.of());
        Mockito.when(gpuResourceLoader.loadGPUInfo()).thenReturn(Set.of());

        val underTest = new ExecutorResourceLoaderImpl(numaResourceLoader, gpuResourceLoader);
        val result = underTest.loadResources();

        verify(numaResourceLoader, Mockito.times(1)).loadSystemResources();
        verify(gpuResourceLoader, Mockito.times(1)).loadGPUInfo();
        assertNotNull(result);
        assertTrue(result.getNumaNodeToResource().isEmpty());
        assertTrue(result.getGpuInfos().isEmpty());
    }

    @Test
    void testNoOpGpuLoaderReturnsEmpty() {
        val numaResourceLoader = Mockito.mock(NumaResourceLoader.class);
        val gpuResourceLoader = new NoOpGPUResourceLoader();
        Mockito.when(numaResourceLoader.loadSystemResources()).thenReturn(
                Map.of(0, ResourceManager.NodeInfo.from(Set.of(1, 2), 1024)));

        val underTest = new ExecutorResourceLoaderImpl(numaResourceLoader, gpuResourceLoader);
        val result = underTest.loadResources();

        assertFalse(result.getNumaNodeToResource().isEmpty());
        assertTrue(result.getGpuInfos().isEmpty());
    }

    @Test
    void testGpuLoaderResultsPreserved() {
        val numaResourceLoader = Mockito.mock(NumaResourceLoader.class);
        val gpuResourceLoader = Mockito.mock(GPUResourceLoader.class);
        val gpuInfo = new GPUInfo("GPU-123", GpuVendor.NVIDIA, "H100", 0);
        Mockito.when(numaResourceLoader.loadSystemResources()).thenReturn(Map.of());
        Mockito.when(gpuResourceLoader.loadGPUInfo()).thenReturn(Set.of(gpuInfo));

        val underTest = new ExecutorResourceLoaderImpl(numaResourceLoader, gpuResourceLoader);
        val result = underTest.loadResources();

        assertEquals(1, result.getGpuInfos().size());
        assertEquals(gpuInfo, result.getGpuInfos().iterator().next());
    }

    @Test
    void testGpuLoaderFailureIsIsolated() {
        val numaResourceLoader = Mockito.mock(NumaResourceLoader.class);
        val gpuResourceLoader = Mockito.mock(GPUResourceLoader.class);
        Mockito.when(numaResourceLoader.loadSystemResources()).thenReturn(
                Map.of(0, ResourceManager.NodeInfo.from(Set.of(1, 2), 1024)));
        Mockito.when(gpuResourceLoader.loadGPUInfo()).thenThrow(new RuntimeException("nvidia-smi crashed"));

        val underTest = new ExecutorResourceLoaderImpl(numaResourceLoader, gpuResourceLoader);
        val result = underTest.loadResources();

        // NUMA resources should still be returned
        assertFalse(result.getNumaNodeToResource().isEmpty());
        // GPU set should be empty due to failure isolation
        assertTrue(result.getGpuInfos().isEmpty());
    }
}