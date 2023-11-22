package com.phonepe.drove.executor.resourcemgmt.resourceloader;

import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class NumaActivationResourceLoader implements ResourceLoader {

    private final ResourceLoader root;
    private final ResourceConfig resourceConfig;

    @Inject
    public NumaActivationResourceLoader(@Named("NumaCtlBasedResourceLoader") ResourceLoader root,
                                        ResourceConfig resourceConfig) {
        this.root = root;
        this.resourceConfig = resourceConfig;
    }

    @Override
    public Map<Integer, ResourceManager.NodeInfo> loadSystemResources() throws Exception {
        val resources = root.loadSystemResources();
        log.info("Disable numa pinning is : {}", resourceConfig.isDisableNUMAPinning() ? "Off" : "On");
        if (resourceConfig.isDisableNUMAPinning()) {
            val availableCores = resources.values().stream()
                    .map(ResourceManager.NodeInfo::getAvailableCores)
                    .flatMap(Set::stream)
                    .collect(Collectors.toSet());
            val availableMemory =
                    resources.values().stream()
                            .map(ResourceManager.NodeInfo::getMemoryInMB)
                            .mapToLong(Long::longValue)
                            .sum();
            return Map.of(0, new ResourceManager.NodeInfo(availableCores, availableMemory));
        }
        return resources;
    }
}
