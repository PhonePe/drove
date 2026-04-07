package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import com.phonepe.drove.models.info.GPUInfo;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.*;

/**
 * Aggregates multiple vendor-specific {@link GPUResourceLoader} implementations.
 * <p>
 * Each loader is invoked independently with error isolation — if one vendor's loader fails,
 * GPUs from other vendors are still returned. This allows the executor to discover GPUs from
 * NVIDIA, AMD, Intel (or any future vendor) in a single pass.
 * <p>
 * Example:
 * <pre>
 *     new CompositeGPUResourceLoader(List.of(
 *         new NVIDIASMIGPUResourceLoader(),   // discovers NVIDIA GPUs
 *         new AMDSMIGPUResourceLoader()       // discovers AMD GPUs (future)
 *     ));
 * </pre>
 */
@Slf4j
public class CompositeGPUResourceLoader implements GPUResourceLoader {

    private final List<GPUResourceLoader> delegates;

    public CompositeGPUResourceLoader(List<GPUResourceLoader> delegates) {
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public Set<GPUInfo> loadGPUInfo() {
        if (delegates.isEmpty()) {
            return Collections.emptySet();
        }
        val result = new HashSet<GPUInfo>();
        for (val loader : delegates) {
            try {
                result.addAll(loader.loadGPUInfo());
            }
            catch (Exception e) {
                log.warn("GPU resource loader {} failed; skipping: {}",
                        loader.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
        return Collections.unmodifiableSet(result);
    }
}

