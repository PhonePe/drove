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

package com.phonepe.drove.executor.resourcemgmt;

import com.google.common.collect.Sets;
import com.phonepe.drove.common.model.utils.Pair;
import com.phonepe.drove.models.info.GPUInfo;
import com.phonepe.drove.models.info.resources.PhysicalLayout;
import com.phonepe.drove.models.info.resources.available.AvailableCPU;
import com.phonepe.drove.models.info.resources.available.AvailableGPU;
import com.phonepe.drove.models.info.resources.available.AvailableMemory;
import io.appform.functionmetrics.MonitoredFunction;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 */
@Slf4j
@Singleton
public class ResourceManager {
    public enum ResourceLockType {
        SOFT,
        HARD
    }

    @Value
    public static class ResourceUsage {
        String id;
        ResourceLockType type;
        Map<Integer, NodeInfo> usedResources;
        List<String> usedGPUs;
    }
    @Value
    public static class ExecutorResources {
        Map<Integer, NodeInfo> numaNodeToResource;
        Set<GPUInfo> gpuInfos;

        public ExecutorResources(Map<Integer, NodeInfo> numaNodeToResource, Set<GPUInfo> gpuInfos) {
            this.numaNodeToResource = Collections.unmodifiableMap(new LinkedHashMap<>(numaNodeToResource));
            this.gpuInfos = Collections.unmodifiableSet(new LinkedHashSet<>(gpuInfos));
        }
    }
    @Data
    @AllArgsConstructor
    public static class NodeInfo {
        private final Set<Integer> physicalCores;
        private final Map<Integer, Integer> vCoreMapping;
        private final long physicalMemoryInMB;
        private Set<Integer> availableCores;
        private long memoryInMB;

        public static NodeInfo from(Set<Integer> availableCores, long memoryInMB) {
            return new NodeInfo(availableCores,
                                availableCores.stream()
                                        .collect(Collectors.toMap(Function.identity(), Function.identity())),
                                memoryInMB,
                                availableCores,
                                memoryInMB);
        }
    }

    private final Map<String, ResourceUsage> resourceLocks = new HashMap<>();
    private final ConsumingFireForgetSignal<ResourceInfo> resourceUpdated
            = ConsumingFireForgetSignal.<ResourceInfo>builder()
            .executorService(Executors.newSingleThreadExecutor())
            .build();
    private Map<Integer, NodeInfo> nodes = Collections.emptyMap();
    private List<String> gpus = Collections.emptyList();
    private Set<GPUInfo> gpuInfoSet = Collections.emptySet();
    private PhysicalLayout physicalLayout = null;

    @MonitoredFunction
    public synchronized void populateResources(ExecutorResources resources) {
        this.nodes = Map.copyOf(resources.getNumaNodeToResource());
        this.gpuInfoSet = Set.copyOf(resources.getGpuInfos());
        this.gpus = List.copyOf(resources.getGpuInfos().stream().map(GPUInfo::getGpuId).collect(Collectors.toList()));
        val cores = new HashMap<Integer, Set<Integer>>();
        val memory = new HashMap<Integer, Long>();
        resources.getNumaNodeToResource().forEach((numaNode, info) -> {
            cores.put(numaNode, info.getPhysicalCores());
            memory.put(numaNode, info.getPhysicalMemoryInMB());
        });
        this.physicalLayout = new PhysicalLayout(cores, memory,
            resources.getGpuInfos().stream().collect(Collectors.toUnmodifiableMap(
                GPUInfo::getGpuId,
                g -> g,
                (left, right) -> left)));
    }

    @MonitoredFunction
    public synchronized boolean lockResources(final ResourceUsage usage) {
        if (resourceLocks.containsKey(usage.getId())) {
            log.error("Resources already allocated for: {}", usage.getId());
            return false;
        }
        val resourceRequirements = usage.getUsedResources();
        if (!nodes.keySet().containsAll(resourceRequirements.keySet())) {
            return false;
        }
        if (!resourceRequirements
                .entrySet()
                .stream()
                .allMatch(entry -> ensureNodeResource(nodes.get(entry.getKey()), entry.getValue()))) {
            log.error("Provided cpu or memory requirement not available. Usage Info: {}", usage);
            return false;
        }
        if (!gpus.containsAll(usage.getUsedGPUs())) {
            log.error("Provided gpu requirement not available. Usage Info: {}", usage);
            return false;
        }
        val currentGpus = new ArrayList<>(gpus);
        for (val gpu : usage.getUsedGPUs()) {
            boolean removed = currentGpus.remove(gpu);
            if (!removed) {
                log.error("GPU '{}' not available for allocation. Usage: {}", gpu, usage);
                return false;
            }
        }

        val currNodes = new HashMap<>(nodes);
        resourceRequirements
                .forEach((node, requirement)
                                 -> currNodes.computeIfPresent(
                        node, (key, old) -> new NodeInfo(
                                old.getPhysicalCores(),
                                old.getVCoreMapping(),
                                old.getPhysicalMemoryInMB(),
                                Set.copyOf(Sets.difference(old.getAvailableCores(), requirement.getAvailableCores())),
                                old.getMemoryInMB() - requirement.getMemoryInMB())));
        gpus = List.copyOf(currentGpus);
        nodes = Map.copyOf(currNodes);
        resourceLocks.put(usage.getId(), usage);
        resourceUpdated.dispatch(calculateResources());
        return true;
    }

    @MonitoredFunction
    public synchronized boolean reclaimResources(String id) {
        if (!resourceLocks.containsKey(id)) {
            log.warn("No recorded usage for id: {}", id);
            return false;
        }

        val usage = resourceLocks.get(id);
        val currNodes = new HashMap<>(nodes);
        val currentGpus = new ArrayList<>(gpus);
        val resourceRequirements = usage.getUsedResources();

        resourceRequirements
                .forEach((node, requirement)
                                 -> currNodes.computeIfPresent(
                        node, (key, old) -> new NodeInfo(
                                old.getPhysicalCores(),
                                old.getVCoreMapping(),
                                old.getPhysicalMemoryInMB(),
                                Set.copyOf(Sets.union(old.getAvailableCores(), requirement.getAvailableCores())),
                                old.getMemoryInMB() + requirement.getMemoryInMB())));
        currentGpus.addAll(usage.getUsedGPUs());
        gpus = List.copyOf(currentGpus);
        nodes = Map.copyOf(currNodes);
        resourceLocks.remove(id);
        resourceUpdated.dispatch(calculateResources());
        return true;
    }

    @MonitoredFunction
    public synchronized ResourceInfo currentState() {
        return calculateResources();
    }

    public ConsumingFireForgetSignal<ResourceInfo> onResourceUpdated() {
        return resourceUpdated;
    }

    private ResourceInfo calculateResources() {
        val cpus = nodes.entrySet()
                .stream()
                .map(entry -> new Pair<>(entry.getKey(), entry.getValue().getAvailableCores()))
                .collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond));
        val usedCores = resourceLocks.values()
                .stream()
                .map(ResourceUsage::getUsedResources)
                .flatMap(usage -> usage.entrySet().stream().map(entry -> new Pair<>(entry.getKey(),
                                                                                    entry.getValue()
                                                                                            .getAvailableCores())))
                .collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond, Sets::union));

        val memory = nodes.entrySet()
                .stream()
                .map(entry -> new Pair<>(entry.getKey(), entry.getValue().getMemoryInMB()))
                .collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond));
        val usedMemory = resourceLocks.values()
                .stream()
                .map(ResourceUsage::getUsedResources)
                .flatMap(usage -> usage.entrySet()
                        .stream()
                        .map(entry -> new Pair<>(entry.getKey(), entry.getValue().getMemoryInMB())))
                .collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond, Long::sum));

        val currGpus = this.gpus.stream()
                .toList();
        val usedGpus = resourceLocks.values()
                .stream()
                .map(ResourceUsage::getUsedGPUs)
                .flatMap(Collection::stream)
                .toList();

        return new ResourceInfo(new AvailableCPU(cpus, usedCores),
                                new AvailableMemory(memory, usedMemory),
                                new AvailableGPU(currGpus, usedGpus),
                                physicalLayout);
    }

    private boolean ensureNodeResource(NodeInfo actual, NodeInfo requirement) {
        if (null == actual) {
            return false;
        }
        return actual.getAvailableCores().containsAll(requirement.getAvailableCores())
                && actual.getMemoryInMB() >= requirement.getMemoryInMB();
    }

}
