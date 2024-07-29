package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.resourcemgmt.OverProvisioning;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.phonepe.drove.executor.ExecutorTestingUtils.discoveredNumaNode;
import static com.phonepe.drove.executor.utils.ExecutorUtils.mapCores;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NumaActivationResourceLoaderTest extends AbstractTestBase {

    @Test
    @SneakyThrows
    void testIfNoChangeIsMadeWhenNumaPinningIsEnabled() {
        val baseLoader = Mockito.mock(ResourceLoader.class);
        val availableCores = Set.of(1, 2, 3, 4);
        val resourceMap = Map.of(0,
                                 ResourceManager.NodeInfo.from(availableCores, 1000));
        Mockito.when(baseLoader.loadSystemResources()).thenReturn(resourceMap);
        val rl = new NumaActivationResourceLoader(baseLoader, ExecutorTestingUtils.resourceConfig());
        val processedResource = rl.loadSystemResources();
        assertEquals(resourceMap, processedResource);
    }

    @Test
    @SneakyThrows
    void testIfNodesAreFlattenedOutIfNumaPinningIsDisabled() {
        val baseLoader = Mockito.mock(ResourceLoader.class);
        val resourceMap
                = Map.of(0, discoveredNumaNode(1), 1, discoveredNumaNode(5));
        Mockito.when(baseLoader.loadSystemResources()).thenReturn(resourceMap);
        val resourceConfig = ExecutorTestingUtils.resourceConfig();
        resourceConfig.setDisableNUMAPinning(true);
        val rl = new NumaActivationResourceLoader(baseLoader, resourceConfig);
        val processedResource = rl.loadSystemResources();
        val combinedAvailableCores = Set.of(1, 2, 3, 4, 5, 6, 7, 8);
        assertEquals(Map.of(0,
                            new ResourceManager.NodeInfo(combinedAvailableCores,
                                                                   mapCores(combinedAvailableCores),
                                                                   2000,
                                                         combinedAvailableCores,
                                                         2000)),
                     processedResource);
    }

    @Test
    @SneakyThrows
    void testIfEmptyNodeIsCreatedForNoNodeResponse() {
        val baseLoader = Mockito.mock(ResourceLoader.class);
        Mockito.when(baseLoader.loadSystemResources()).thenReturn(Collections.emptyMap());
        val resourceConfig = ExecutorTestingUtils.resourceConfig();
        resourceConfig.setDisableNUMAPinning(true);
        val rl = new NumaActivationResourceLoader(baseLoader, resourceConfig);
        val processedResource = rl.loadSystemResources();
        assertEquals(Map.of(0,
                            new ResourceManager.NodeInfo(Set.of(), Map.of(), 0, Set.of(), 0)),
                     processedResource);
    }

    @Test
    @SneakyThrows
    void testOverProvisioningWithNumaDisabled() {
        val baseLoader = Mockito.mock(ResourceLoader.class);
        val resourceMap
                = Map.of(0, discoveredNumaNode(1), 1, discoveredNumaNode(5));
        Mockito.when(baseLoader.loadSystemResources()).thenReturn(resourceMap);
        val resourceConfig = ExecutorTestingUtils.resourceConfig();
        resourceConfig.setDisableNUMAPinning(true)
                .setOverProvisioning(new OverProvisioning(true, 3, 2));
        val rl = new NumaActivationResourceLoader(new OverProvisioningResourceLoader(baseLoader, resourceConfig), resourceConfig);
        val processedResource = rl.loadSystemResources();
        val combinedAvailableCores = Set.of(1, 2, 3, 4, 5, 6, 7, 8);
        val coreMapping = coreMappings();

        assertEquals(Map.of(0,
                            new ResourceManager.NodeInfo(combinedAvailableCores,
                                                         coreMapping,
                                                         2000,
                                                         coreMapping.keySet(),
                                                         4000)),
                     processedResource);
    }

    private static HashMap<Integer, Integer> coreMappings() {
        val coreMapping = new HashMap<Integer, Integer>();
        coreMapping.put(23,1);
        coreMapping.put( 24,1);
        coreMapping.put( 25,1);
        coreMapping.put( 34,2);
        coreMapping.put( 35,2);
        coreMapping.put( 36,2);
        coreMapping.put( 45,3);
        coreMapping.put( 46,3);
        coreMapping.put( 47,3);
        coreMapping.put( 56,4);
        coreMapping.put( 57,4);
        coreMapping.put( 58,4);
        coreMapping.put( 67,5);
        coreMapping.put( 68,5);
        coreMapping.put( 69,5);
        coreMapping.put( 78,6);
        coreMapping.put( 79,6);
        coreMapping.put( 80,6);
        coreMapping.put( 89,7);
        coreMapping.put( 90,7);
        coreMapping.put( 91,7);
        coreMapping.put(100,8);
        coreMapping.put(101,8);
        coreMapping.put(102,8);
        return coreMapping;
    }

}