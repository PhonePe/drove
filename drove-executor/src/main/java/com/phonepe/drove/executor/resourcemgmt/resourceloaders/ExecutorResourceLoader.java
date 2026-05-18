package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import com.phonepe.drove.executor.resourcemgmt.ResourceManager;

/**
 * Loads the complete resource inventory for an executor node.
 * Implementations aggregate NUMA, CPU, memory, and GPU information into a unified resource snapshot.
 */
public interface ExecutorResourceLoader {

    /**
     * Discovers and returns the full set of resources available on this executor.
     *
     * @return the executor's aggregated resource information
     */
    ResourceManager.ExecutorResources loadResources();
}
