package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.resourcemgmt.OverProvisioning;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
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
                new OverProvisioning(true, 2, 2)
                                          );
        val rl = new OverProvisioningResourceLoader(baseLoader, resourceConfig);
        val processedResource = rl.loadSystemResources();
        val mappedCores = Set.of(11, 12, 16, 17, 21, 22, 26, 27);
        val vCoreMapping = Map.of(
                11, 1,
                12, 1,
                16, 2,
                17, 2,
                21, 3,
                22, 3,
                26, 4,
                27, 4);
        assertEquals(Map.of(0,
                            new ResourceManager.NodeInfo(
                                    rootNodeInfo.getPhysicalCores(),
                                    vCoreMapping,
                                    rootNodeInfo.getPhysicalMemoryInMB(),
                                    mappedCores,
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
}