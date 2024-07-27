package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import com.phonepe.drove.executor.ExecutorCoreModule;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

@Slf4j
@Singleton
public class OverProvisioningResourceLoader implements ResourceLoader {

    private final ResourceLoader root;
    private final ResourceConfig resourceConfig;
    private final int cpuMultiplier;
    private final int memoryMultiplier;

    @Inject
    public OverProvisioningResourceLoader(
            @Named(ExecutorCoreModule.ResourceLoaderIdentifiers.NUMA_ACTIVATION_RESOURCE_LOADER) ResourceLoader root,
            ResourceConfig resourceConfig) {
        this.resourceConfig = resourceConfig;
        this.root = root;
        this.cpuMultiplier = ExecutorUtils.cpuMultiplier(resourceConfig);
        this.memoryMultiplier = resourceConfig
                .getOverProvisioning()
                .getMemoryMultiplier();
    }

    @Override
    public Map<Integer, ResourceManager.NodeInfo> loadSystemResources() {
        val resources = root.loadSystemResources();
        log.info("Over Provisioning is : {}", resourceConfig.getOverProvisioning().isEnabled() ? "On" : "Off");
        if (!resourceConfig.getOverProvisioning().isEnabled()) {
            return resources;
        }
        log.info("Over Provisioning CPU by : {} and Memory by : {}",
                 resourceConfig.getOverProvisioning().getCpuMultiplier(),
                 resourceConfig.getOverProvisioning().getMemoryMultiplier());
        val maxCore = resources.values()
                .stream()
                .map(ResourceManager.NodeInfo::getPhysicalCores)
                .flatMap(Set::stream)
                .max(Integer::compareTo).orElse(0);

        val primeMultiplier = nextPrime(maxCore);
        log.info("Max core: {} Prime Multiplier: {}", maxCore, primeMultiplier);
        val result = new HashMap<Integer, ResourceManager.NodeInfo>();
        val vCoreMappings = new HashMap<Integer, Integer>();
        resources.forEach((numaNode, info) -> {
            val cores = info.getAvailableCores();
            cores.stream()
                    .sorted()
                    .forEach(actualCore -> {
                        val generated = IntStream.rangeClosed(1, cpuMultiplier)
                                .map(i -> actualCore * primeMultiplier + i)
                                .boxed()
                                .sorted()
                                .toList();
                        log.info("NUMANode: {} Actual Core: {} -> Generated VCores: {}",
                                 numaNode, actualCore, generated);
                        generated.forEach(vc -> vCoreMappings.put(vc, actualCore));
                    });
            val generatedMem = info.getPhysicalMemoryInMB() * memoryMultiplier;
            result.put(numaNode, new ResourceManager.NodeInfo(info.getPhysicalCores(),
                                                              vCoreMappings,
                                                              info.getPhysicalMemoryInMB(),
                                                              vCoreMappings.keySet(),
                                                              generatedMem));
            log.info("Node: {} Original Memory: {} Generated: {}", numaNode, info.getMemoryInMB(), generatedMem);
        });

        return result;
    }

    int nextPrime(int m) {
        return Integer.parseInt(new BigInteger(Integer.toString(m)).nextProbablePrime().toString());
    }
}
