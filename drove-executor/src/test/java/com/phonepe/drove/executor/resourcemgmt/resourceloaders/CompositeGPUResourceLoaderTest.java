package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import com.phonepe.drove.models.application.requirements.GpuVendor;
import com.phonepe.drove.models.info.GPUInfo;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CompositeGPUResourceLoaderTest {

    @Test
    void testEmptyDelegatesReturnsEmpty() {
        val loader = new CompositeGPUResourceLoader(List.of());
        assertTrue(loader.loadGPUInfo().isEmpty());
    }

    @Test
    void testSingleDelegateReturnsItsGpus() {
        val nvidiaLoader = Mockito.mock(GPUResourceLoader.class);
        val gpu = new GPUInfo("GPU-1", GpuVendor.NVIDIA, "H100", 0);
        Mockito.when(nvidiaLoader.loadGPUInfo()).thenReturn(Set.of(gpu));

        val composite = new CompositeGPUResourceLoader(List.of(nvidiaLoader));
        val result = composite.loadGPUInfo();
        assertEquals(1, result.size());
        assertTrue(result.contains(gpu));
    }

    @Test
    void testMultipleDelegatesAggregateResults() {
        val nvidiaLoader = Mockito.mock(GPUResourceLoader.class);
        val amdLoader = Mockito.mock(GPUResourceLoader.class);
        val nvidiaGpu = new GPUInfo("GPU-NV1", GpuVendor.NVIDIA, "H100", 0);
        val amdGpu = new GPUInfo("GPU-AMD1", GpuVendor.AMD, "MI300X", 1);
        Mockito.when(nvidiaLoader.loadGPUInfo()).thenReturn(Set.of(nvidiaGpu));
        Mockito.when(amdLoader.loadGPUInfo()).thenReturn(Set.of(amdGpu));

        val composite = new CompositeGPUResourceLoader(List.of(nvidiaLoader, amdLoader));
        val result = composite.loadGPUInfo();
        assertEquals(2, result.size());
        assertTrue(result.contains(nvidiaGpu));
        assertTrue(result.contains(amdGpu));
    }

    @Test
    void testFailingDelegateDoesNotAffectOthers() {
        val nvidiaLoader = Mockito.mock(GPUResourceLoader.class);
        val amdLoader = Mockito.mock(GPUResourceLoader.class);
        val amdGpu = new GPUInfo("GPU-AMD1", GpuVendor.AMD, "MI300X", 1);
        Mockito.when(nvidiaLoader.loadGPUInfo()).thenThrow(new RuntimeException("nvidia-smi crashed"));
        Mockito.when(amdLoader.loadGPUInfo()).thenReturn(Set.of(amdGpu));

        val composite = new CompositeGPUResourceLoader(List.of(nvidiaLoader, amdLoader));
        val result = composite.loadGPUInfo();
        assertEquals(1, result.size());
        assertTrue(result.contains(amdGpu));
    }

    @Test
    void testAllDelegatesFailReturnsEmpty() {
        val nvidiaLoader = Mockito.mock(GPUResourceLoader.class);
        val amdLoader = Mockito.mock(GPUResourceLoader.class);
        Mockito.when(nvidiaLoader.loadGPUInfo()).thenThrow(new RuntimeException("nvidia-smi crashed"));
        Mockito.when(amdLoader.loadGPUInfo()).thenThrow(new RuntimeException("rocm-smi crashed"));

        val composite = new CompositeGPUResourceLoader(List.of(nvidiaLoader, amdLoader));
        assertTrue(composite.loadGPUInfo().isEmpty());
    }

    @Test
    void testResultSetIsUnmodifiable() {
        val nvidiaLoader = Mockito.mock(GPUResourceLoader.class);
        val gpu = new GPUInfo("GPU-1", GpuVendor.NVIDIA, "H100", 0);
        Mockito.when(nvidiaLoader.loadGPUInfo()).thenReturn(Set.of(gpu));

        val composite = new CompositeGPUResourceLoader(List.of(nvidiaLoader));
        val result = composite.loadGPUInfo();
        assertThrows(UnsupportedOperationException.class,
                () -> result.add(new GPUInfo("GPU-2", GpuVendor.AMD, "MI300X", 1)));
    }
}
