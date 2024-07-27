package com.phonepe.drove.executor.resourcemgmt;

import com.google.common.collect.Sets;
import com.phonepe.drove.common.model.utils.Pair;
import com.phonepe.drove.models.info.resources.PhysicalLayout;
import com.phonepe.drove.models.info.resources.available.AvailableCPU;
import com.phonepe.drove.models.info.resources.available.AvailableMemory;
import io.appform.functionmetrics.MonitoredFunction;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
    private PhysicalLayout physicalLayout = null;

    @MonitoredFunction
    public synchronized void populateResources(Map<Integer, NodeInfo> nodes) {
        this.nodes = Map.copyOf(nodes);
        val cores = new HashMap<Integer, Set<Integer>>();
        val memory = new HashMap<Integer, Long>();
        nodes.forEach((numaNode, info) -> {
            cores.put(numaNode, info.getPhysicalCores());
            memory.put(numaNode, info.getPhysicalMemoryInMB());
        });
        this.physicalLayout = new PhysicalLayout(cores, memory);
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

        return new ResourceInfo(new AvailableCPU(cpus, usedCores),
                                new AvailableMemory(memory, usedMemory),
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
