package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import com.phonepe.drove.models.info.GPUInfo;

import java.util.Set;

/**
 * Discovers GPU devices available on the executor node.
 * Implementations detect installed GPUs and return their identifying information.
 */
public interface GPUResourceLoader {

    /**
     * Loads information about all available GPU devices on this node.
     *
     * @return set of discovered GPU device information
     */
    Set<GPUInfo> loadGPUInfo();
}
