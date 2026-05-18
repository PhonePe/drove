package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import com.google.inject.Inject;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.models.info.GPUInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Set;

/**
 * Composite resource loader that delegates to NUMA and GPU sub-loaders.
 * <p>
 * Config-driven decisions (e.g. whether GPU loading is enabled) are handled in the Guice module
 * by binding {@link GPUResourceLoader} to either a real loader or {@link NoOpGPUResourceLoader}.
 * This class always calls both sub-loaders unconditionally.
 * <p>
 * GPU loader failures are isolated — if the GPU loader throws, NUMA resources are still returned
 * with an empty GPU set, and the executor can start without GPU support.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ExecutorResourceLoaderImpl implements ExecutorResourceLoader {
    private final NumaResourceLoader numaResourceLoader;
    private final GPUResourceLoader gpuResourceLoader;

    @Override
    public ResourceManager.ExecutorResources loadResources() {
        var numaResources = numaResourceLoader.loadSystemResources();
        var gpuInfos = loadGPUInfoSafely();
        return new ResourceManager.ExecutorResources(numaResources, gpuInfos);
    }

    private Set<GPUInfo> loadGPUInfoSafely() {
        try {
            return gpuResourceLoader.loadGPUInfo();
        }
        catch (Exception e) {
            log.warn("GPU resource loading failed; executor will start without GPU support: {}", e.getMessage(), e);
            return Collections.emptySet();
        }
    }
}
