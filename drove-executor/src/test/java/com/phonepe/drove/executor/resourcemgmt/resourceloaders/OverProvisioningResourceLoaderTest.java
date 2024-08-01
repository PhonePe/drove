package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.resourcemgmt.OverProvisioning;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static com.phonepe.drove.executor.ExecutorTestingUtils.discoveredNumaNode;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OverProvisioningResourceLoaderTest {
    @Test
    @SneakyThrows
    void testIfNoChangeIsMadeWhenOverProvisioningIsDisabled() {
        val baseLoader = Mockito.mock(ResourceLoader.class);
        val resourceMap = Map.of(0, discoveredNumaNode());
        Mockito.when(baseLoader.loadSystemResources()).thenReturn(resourceMap);
        val rl = new OverProvisioningResourceLoader(baseLoader, ExecutorTestingUtils.resourceConfig());
        val processedResource = rl.loadSystemResources();
        assertEquals(resourceMap, processedResource);
    }

    @Test
    @SneakyThrows
    void testIfResourcesAreOverProvisionedIfOverProvisioningIsEnabled() {
        val baseLoader = Mockito.mock(ResourceLoader.class);
        val rootNodeInfo = discoveredNumaNode();
        val resourceMap = Map.of(0, rootNodeInfo);
        Mockito.when(baseLoader.loadSystemResources()).thenReturn(resourceMap);
        val resourceConfig = ExecutorTestingUtils.resourceConfig();
        resourceConfig.setOverProvisioning(
                new OverProvisioning(true, 2, 2));
        val rl = new OverProvisioningResourceLoader(baseLoader, resourceConfig);
        val processedResource = rl.loadSystemResources();
        val vCoreMapping = Map.of(
                7, 1,
                8, 1,
                10, 2,
                11, 2,
                13, 3,
                14, 3,
                16, 4,
                17, 4);
        assertEquals(Map.of(0,
                            new ResourceManager.NodeInfo(
                                    rootNodeInfo.getPhysicalCores(),
                                    vCoreMapping,
                                    rootNodeInfo.getPhysicalMemoryInMB(),
                                    vCoreMapping.keySet(),
                                    2000)), processedResource);
    }

    @Test
    @SneakyThrows
    void testIfEmptyNodeIsCreatedForNoNodeResponseWithOverProvisioningEnabled() {
        val baseLoader = Mockito.mock(ResourceLoader.class);
        Mockito.when(baseLoader.loadSystemResources()).thenReturn(Collections.emptyMap());
        val resourceConfig = ExecutorTestingUtils.resourceConfig();
        resourceConfig.setOverProvisioning(
                new OverProvisioning(true, 2, 2)
                                          );
        val rl = new OverProvisioningResourceLoader(baseLoader, resourceConfig);
        val processedResource = rl.loadSystemResources();
        assertEquals(Map.of(), processedResource);
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource({"4,20", "20,4", "20,20"})
    void testNoOverlap(int numCores, int cpuMultiplier) {
        val baseLoader = Mockito.mock(ResourceLoader.class);
        val rootNodeInfo = discoveredNumaNode(0, numCores);
        val resourceMap = Map.of(0, rootNodeInfo);
        Mockito.when(baseLoader.loadSystemResources()).thenReturn(resourceMap);
        val resourceConfig = ExecutorTestingUtils.resourceConfig();

        resourceConfig.setOverProvisioning(
                new OverProvisioning(true, cpuMultiplier, 1));
        val rl = new OverProvisioningResourceLoader(baseLoader, resourceConfig);
        val processedResource = rl.loadSystemResources();
        System.out.println(processedResource);
        val out = processedResource.values()
                .stream()
                .map(ResourceManager.NodeInfo::getAvailableCores)
                .flatMap(Set::stream)
                .toList();
        assertEquals(numCores * cpuMultiplier, out.size());
        assertEquals(Set.copyOf(out).size(), out.size());
    }
}