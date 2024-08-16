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

import com.phonepe.drove.executor.ExecutorCoreModule;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Singleton
public class NumaActivationResourceLoader implements ResourceLoader {

    private final ResourceLoader root;
    private final ResourceConfig resourceConfig;

    @Inject
    public NumaActivationResourceLoader(
            @Named(ExecutorCoreModule.ResourceLoaderIdentifiers.OVERPROVISIONIN_RESOURCE_LOADER) ResourceLoader root,
            ResourceConfig resourceConfig) {
        this.root = root;
        this.resourceConfig = resourceConfig;
    }

    @Override
    public Map<Integer, ResourceManager.NodeInfo> loadSystemResources() {
        val resources = root.loadSystemResources();
        log.info("Numa pinning is : {}", resourceConfig.isDisableNUMAPinning() ? "Off" : "On");
        if (resourceConfig.isDisableNUMAPinning()) {
            val physicalCores = new HashSet<Integer>();
            var physicalMemory = new AtomicLong(0);
            val vCores = new HashMap<Integer, Integer>();
            val availableCores = new HashSet<Integer>();
            var availableMemory = new AtomicLong(0);
            resources.values()
                            .forEach(nodeInfo -> {
                                physicalCores.addAll(nodeInfo.getPhysicalCores());
                                physicalMemory.addAndGet(nodeInfo.getPhysicalMemoryInMB());
                                vCores.putAll(nodeInfo.getVCoreMapping());
                                availableCores.addAll(nodeInfo.getAvailableCores());
                                availableMemory.addAndGet(nodeInfo.getMemoryInMB());
                            });
            return Map.of(0,
                          new ResourceManager.NodeInfo(physicalCores,
                                                       vCores,
                                                       physicalMemory.get(),
                                                       availableCores,
                                                       availableMemory.get()));
        }
        return resources;
    }
}
