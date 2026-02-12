package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import com.google.inject.Inject;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Set;

@RequiredArgsConstructor(onConstructor=@__({@Inject}))
public class ExecutorResourceLoaderImpl implements ExecutorResourceLoader {
    private final ResourceConfig resourceConfig;
    private final NumaResourceLoader numaResourceLoader;
    private final GPUResourceLoader gpuResourceLoader;

    @Override
    public ResourceManager.ExecutorResources loadResources() {
        if (resourceConfig.getNvidiaGPULoaderConfig() != null && resourceConfig.getNvidiaGPULoaderConfig().isEnabled()) {
            return new ResourceManager.ExecutorResources(numaResourceLoader.loadSystemResources(), gpuResourceLoader.loadGPUInfo());
        } else {
            return new ResourceManager.ExecutorResources(numaResourceLoader.loadSystemResources(), Set.of());
        }
    }
}
