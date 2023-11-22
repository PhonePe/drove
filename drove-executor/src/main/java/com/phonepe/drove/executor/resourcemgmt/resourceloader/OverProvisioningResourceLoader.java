package com.phonepe.drove.executor.resourcemgmt.resourceloader;

import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Map;
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
            if (!resourceConfig.isDisableNUMAPinning()) {
                throw new IllegalStateException("Numa pinning has to be disabled for over provisioning");
            }
            if (resources.size() != 1) {
                throw new IllegalStateException("Over provisioning can be enabled only if 1 node is allocated");
            }
            val nodeInfo = resources.entrySet().iterator().next().getValue();
            return Map.of(0,
                    new ResourceManager.NodeInfo(
                            IntStream.rangeClosed(0,
                                            nodeInfo.getAvailableCores().size() *
                                                    resourceConfig
                                                            .getOverProvisioningConfiguration()
                                                            .getCpuOverProvisioningMultiplier() - 1)
                                    .boxed().collect(Collectors.toSet())
                            , nodeInfo.getMemoryInMB() *
                            resourceConfig.getOverProvisioningConfiguration().getMemoryOverProvisioningMultiplier()));
        }
        return resources;
    }
}
