/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

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
        coreMapping.put(11,1);
        coreMapping.put( 12,1);
        coreMapping.put( 13,1);
        coreMapping.put( 16,2);
        coreMapping.put( 17,2);
        coreMapping.put( 18,2);
        coreMapping.put( 21,3);
        coreMapping.put( 22,3);
        coreMapping.put( 23,3);
        coreMapping.put( 26,4);
        coreMapping.put( 27,4);
        coreMapping.put( 28,4);
        coreMapping.put( 31,5);
        coreMapping.put( 32,5);
        coreMapping.put( 33,5);
        coreMapping.put( 36,6);
        coreMapping.put( 37,6);
        coreMapping.put( 38,6);
        coreMapping.put( 41,7);
        coreMapping.put( 42,7);
        coreMapping.put( 43,7);
        coreMapping.put( 46,8);
        coreMapping.put( 47,8);
        coreMapping.put( 48,8);
        return coreMapping;
    }

}