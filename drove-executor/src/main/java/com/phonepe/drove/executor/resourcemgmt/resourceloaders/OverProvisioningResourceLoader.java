package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import com.phonepe.drove.executor.ExecutorCoreModule;
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

    private final ResourceLoader root;
    private final ResourceConfig resourceConfig;

    @Inject
    public OverProvisioningResourceLoader(@Named(ExecutorCoreModule.ResourceLoaderIdentifiers.NUMA_ACTIVATION_RESOURCE_LOADER) ResourceLoader root,
                                          ResourceConfig resourceConfig) {
        this.resourceConfig = resourceConfig;
        this.root = root;
    }

    @Override
    public Map<Integer, ResourceManager.NodeInfo> loadSystemResources() {
        val resources = root.loadSystemResources();
        log.info("Over Provisioning is : {}", resourceConfig.getOverProvisioning().isOverProvisioningUpEnabled() ? "On" : "Off");
        if (resourceConfig.getOverProvisioning().isOverProvisioningUpEnabled()) {
            log.info("Over Provisioning CPU by : {} and Memory by : {}",
                    resourceConfig.getOverProvisioning().getCpuOverProvisioningMultiplier(),
                    resourceConfig.getOverProvisioning().getMemoryOverProvisioningMultiplier());
            return Map.of(0,
                    new ResourceManager.NodeInfo(
                            IntStream.rangeClosed(0, resources.values().stream()
                                            .map(ResourceManager.NodeInfo::getAvailableCores)
                                            .mapToInt(Set::size)
                                            .sum() * resourceConfig
                                            .getOverProvisioning()
                                            .getCpuOverProvisioningMultiplier() - 1)
                                    .boxed().collect(Collectors.toSet()),
                            resources.values().stream()
                                    .mapToLong(ResourceManager.NodeInfo::getMemoryInMB)
                                    .sum() * resourceConfig
                                    .getOverProvisioning()
                                    .getMemoryOverProvisioningMultiplier()));
        }
        return resources;
    }
}
