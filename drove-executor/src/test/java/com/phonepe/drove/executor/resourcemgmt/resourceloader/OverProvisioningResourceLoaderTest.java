package com.phonepe.drove.executor.resourcemgmt.resourceloader;

import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.resourcemgmt.OverProvisioningConfiguration;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class OverProvisioningResourceLoaderTest {
    @Test
    @SneakyThrows
    void testIfNoChangeIsMadeWhenOverProvisioningIsDisabled() {
        val baseLoader = Mockito.mock(ResourceLoader.class);
        val resourceMap = Map.of(0, new ResourceManager.NodeInfo(Set.of(1, 2, 3, 4), 1000));
        Mockito.when(baseLoader.loadSystemResources()).thenReturn(resourceMap);
        val rl = new OverProvisioningResourceLoader(baseLoader, ExecutorTestingUtils.resourceConfig());
        val processedResource = rl.loadSystemResources();
        assertEquals(resourceMap, processedResource);
    }

    @Test
    @SneakyThrows
    void testIfResourcesAreOverProvisionedIfOverProvisioningIsEnabled() {
        val baseLoader = Mockito.mock(ResourceLoader.class);
        val resourceMap = Map.of(0, new ResourceManager.NodeInfo(Set.of(1, 2, 3, 4), 1000));
        Mockito.when(baseLoader.loadSystemResources()).thenReturn(resourceMap);
        val resourceConfig = ExecutorTestingUtils.resourceConfig();
        resourceConfig.setOverProvisioningConfiguration(
                new OverProvisioningConfiguration(true,2,2)
        );
        val rl = new OverProvisioningResourceLoader(baseLoader, resourceConfig);
        val processedResource = rl.loadSystemResources();
        assertEquals(Map.of(0, new ResourceManager.NodeInfo(IntStream.rangeClosed(0,7).boxed().collect(Collectors.toSet()), 2000)), processedResource);
    }

    @Test
    @SneakyThrows
    void testIfEmptyNodeIsCreatedForNoNodeResponseWithOverProvisioningEnabled() {
        val baseLoader = Mockito.mock(ResourceLoader.class);
        Mockito.when(baseLoader.loadSystemResources()).thenReturn(Collections.emptyMap());
        val resourceConfig = ExecutorTestingUtils.resourceConfig();
        resourceConfig.setOverProvisioningConfiguration(
                new OverProvisioningConfiguration(true,2,2)
        );
        val rl = new OverProvisioningResourceLoader(baseLoader, resourceConfig);
        val processedResource = rl.loadSystemResources();
        assertEquals(Map.of(0, new ResourceManager.NodeInfo(Collections.emptySet(), 0)), processedResource);
    }
}