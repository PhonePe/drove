/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.executor.managed;

import com.phonepe.drove.executor.AbstractExecutorEngineEnabledTestBase;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.resourcemgmt.resourceloaders.ResourceLoader;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.Map;
import java.util.Set;


/**
 *
 */
class ResourcePopulatorTest extends AbstractExecutorEngineEnabledTestBase {

    @Test
    @SneakyThrows
    void testResourcePopulatorLoadsPopulatedResource() {
        val resourceDB = Mockito.mock(ResourceManager.class);
        val resourceLoader = Mockito.mock(ResourceLoader.class);
        val availableCores0 = Set.of(1, 2, 3, 4);
        val discoveredNodeInfo = ResourceManager.NodeInfo.from(availableCores0, 1000);
        val resource = Map.of(0,
                              discoveredNodeInfo);
        Mockito.when(resourceLoader.loadSystemResources()).thenReturn(resource);
        val resourcePopulator = new ResourcePopulator(resourceDB, resourceLoader);
        resourcePopulator.start();
        Mockito.verify(resourceDB, Mockito.times(1))
                .populateResources(Map.of(0, discoveredNodeInfo));
        resourcePopulator.stop();
    }


}