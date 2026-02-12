package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import com.phonepe.drove.executor.resourcemgmt.NvidiaGPULoaderConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import lombok.val;
import org.checkerframework.checker.units.qual.N;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

class ExecutorResourceLoaderImplTest {
    @Test
    void testExecutorResourceLoading() {
        val resourceConfig = ResourceConfig.builder()

                .build();
        val numaResourceLoader = Mockito.mock(NumaResourceLoader.class);
        val gpuResourceLoader = Mockito.mock(GPUResourceLoader.class);
        val underTest = new ExecutorResourceLoaderImpl(resourceConfig, numaResourceLoader, gpuResourceLoader);
        underTest.loadResources();
        verify(numaResourceLoader, Mockito.times(1)).loadSystemResources();
        verify(gpuResourceLoader, Mockito.never()).loadGPUInfo();
    }

    @Test
    void testExecutorResourceLoadingWithGPU() {
        val resourceConfig = ResourceConfig.builder()
                .nvidiaGPULoaderConfig(new NvidiaGPULoaderConfig(true))
                .build();
        val numaResourceLoader = Mockito.mock(NumaResourceLoader.class);
        val gpuResourceLoader = Mockito.mock(GPUResourceLoader.class);
        val underTest = new ExecutorResourceLoaderImpl(resourceConfig, numaResourceLoader, gpuResourceLoader);
        underTest.loadResources();
        verify(numaResourceLoader, Mockito.times(1)).loadSystemResources();
        verify(gpuResourceLoader, Mockito.times(1)).loadGPUInfo();
    }
}