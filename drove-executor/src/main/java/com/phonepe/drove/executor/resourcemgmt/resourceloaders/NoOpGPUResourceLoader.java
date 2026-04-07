package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import com.phonepe.drove.models.info.GPUInfo;

import java.util.Collections;
import java.util.Set;

/**
 * A no-op GPU resource loader that returns an empty set.
 * Bound by Guice when GPU loading is disabled in config.
 */
public class NoOpGPUResourceLoader implements GPUResourceLoader {
    @Override
    public Set<GPUInfo> loadGPUInfo() {
        return Collections.emptySet();
    }
}

