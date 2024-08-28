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
import com.phonepe.drove.executor.resourcemgmt.OverProvisioning;
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
import java.util.Objects;
import java.util.stream.IntStream;

@Slf4j
@Singleton
public class OverProvisioningResourceLoader implements ResourceLoader {

    private final ResourceLoader root;
    private final OverProvisioning overProvisioning;
    private final int cpuMultiplier;
    private final int memoryMultiplier;

    @Inject
    public OverProvisioningResourceLoader(
            @Named(ExecutorCoreModule.ResourceLoaderIdentifiers.NUMA_CTL_BASED_RESOURCE_LOADER) ResourceLoader root,
            ResourceConfig resourceConfig) {
        this.overProvisioning = Objects.requireNonNullElse(
                resourceConfig.getOverProvisioning(), OverProvisioning.DEFAULT);
        this.root = root;
        this.cpuMultiplier = ExecutorUtils.cpuMultiplier(resourceConfig);
        this.memoryMultiplier = overProvisioning.getMemoryMultiplier();
    }

    @Override
    public Map<Integer, ResourceManager.NodeInfo> loadSystemResources() {
        val resources = root.loadSystemResources();
        log.info("Over Provisioning is : {}", overProvisioning.isEnabled() ? "On" : "Off");
        if (!overProvisioning.isEnabled()) {
            return resources;
        }
        log.info("Over Provisioning CPU by : {} and Memory by : {}", cpuMultiplier, memoryMultiplier);

        val primeMultiplier = nextPrime(cpuMultiplier);
        log.info("CPU Multiplier: {} Prime Multiplier: {}", cpuMultiplier, primeMultiplier);
        val result = new HashMap<Integer, ResourceManager.NodeInfo>();
        val vCoreMappings = new HashMap<Integer, Integer>();
        resources.forEach((numaNode, info) -> {
            val cores = info.getAvailableCores();
            cores.stream()
                    .sorted()
                    .forEach(actualCore -> {
                        val base = (actualCore + 1) * primeMultiplier;
                        val generated = IntStream.rangeClosed(1, cpuMultiplier)
                                .map(i -> base + i)
                                .boxed()
                                .sorted()
                                .toList();
                        log.info("NUMA Node: {} Actual Core: {} -> Generated VCores: {}",
                                 numaNode, actualCore, generated);
                        generated.forEach(vc -> vCoreMappings.put(vc, actualCore));
                    });
            val generatedMem = info.getPhysicalMemoryInMB() * memoryMultiplier;
            result.put(numaNode, new ResourceManager.NodeInfo(info.getPhysicalCores(),
                                                              vCoreMappings,
                                                              info.getPhysicalMemoryInMB(),
                                                              vCoreMappings.keySet(),
                                                              generatedMem));
            log.info("NUMA Node: {} Original Memory: {} Generated: {}", numaNode, info.getMemoryInMB(), generatedMem);
        });

        return result;
    }

    private int nextPrime(int m) {
        return Integer.parseInt(new BigInteger(Integer.toString(m)).nextProbablePrime().toString());
    }
}
