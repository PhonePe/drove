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
import java.util.stream.IntStream;

@Slf4j
@Singleton
public class OverProvisioningResourceLoader implements ResourceLoader {

    private final ResourceConfig resourceConfig;
    private final ResourceLoader root;

    @Inject
    public OverProvisioningResourceLoader(ResourceConfig resourceConfig,
                                          @Named("NumaActivationResourceLoader") ResourceLoader root) {
        this.resourceConfig = resourceConfig;
        this.root = root;
    }

    @Override
    public Map<Integer, ResourceManager.NodeInfo> loadSystemResources() throws Exception {
        val resources = root.loadSystemResources();
        log.info("Disable numa pinning is : {}", resourceConfig.isDisableNUMAPinning() ? "Off" : "On");
        if (resourceConfig.getOverProvisioningConfiguration().isOverProvisioningUpEnabled()) {
            return Map.of(0,
                    new ResourceManager.NodeInfo(IntStream.rangeClosed(0, resources.values().stream()
                                    .map(ResourceManager.NodeInfo::getAvailableCores)
                                    .mapToInt(Set::size)
                                    .sum()
                                    * resourceConfig
                                    .getOverProvisioningConfiguration()
                                    .getCpuOverProvisioningMultiplier() - 1)
                            .boxed().collect(Collectors.toSet()),
                            resources.values().stream()
                                    .mapToLong(ResourceManager.NodeInfo::getMemoryInMB)
                                    .sum()
                                    * resourceConfig.getOverProvisioningConfiguration().getMemoryOverProvisioningMultiplier()));
        }
        return resources;
    }
}
