package com.phonepe.drove.executor.resourcemgmt;

import com.google.common.collect.Sets;
import com.phonepe.drove.models.info.resources.available.AvailableCPU;
import com.phonepe.drove.models.info.resources.available.AvailableMemory;
import com.phonepe.drove.common.model.utils.Pair;
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
import java.util.stream.Collectors;

/**
 *
 */
@Slf4j
@Singleton
public class ResourceDB {
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
        private Set<Integer> availableCores;
        private long memoryInMB;
    }

    private Map<Integer, NodeInfo> nodes = Collections.emptyMap();
    private final Map<String, ResourceUsage> resourceLocks = new HashMap<>();
    private final ConsumingFireForgetSignal<ResourceInfo> resourceUpdated
            = ConsumingFireForgetSignal.<ResourceInfo>builder()
            .executorService(Executors.newSingleThreadExecutor())
            .build();

    public synchronized void populateResources(Map<Integer, NodeInfo> nodes) {
        this.nodes = Map.copyOf(nodes);
    }

    public synchronized boolean lockResources(final ResourceUsage usage) {
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
                        node, (key, old) -> new NodeInfo(Set.copyOf(
                                Sets.difference(old.getAvailableCores(), requirement.getAvailableCores())),
                                                         old.getMemoryInMB() - requirement.getMemoryInMB())));
        nodes = Map.copyOf(currNodes);
        resourceLocks.put(usage.getId(), usage);
        return true;
    }

    public synchronized void reclaimResources(String id) {
        val usage = resourceLocks.get(id);
        if (null == usage) {
            log.warn("No recorded usage for id: {}", id);
            return;
        }
        val currNodes = new HashMap<>(nodes);
        val resourceRequirements = usage.getUsedResources();
        resourceRequirements
                .forEach((node, requirement)
                                 -> currNodes.computeIfPresent(
                        node, (key, old) -> new NodeInfo(Set.copyOf(
                                Sets.union(old.getAvailableCores(), requirement.getAvailableCores())),
                                                         old.getMemoryInMB() + requirement.getMemoryInMB())));
        nodes = Map.copyOf(currNodes);
        resourceLocks.remove(id);
        resourceUpdated.dispatch(calculateResources());
    }


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
                .flatMap(usage -> usage.entrySet().stream().map(entry -> new Pair<>(entry.getKey(), entry.getValue().getMemoryInMB())))
                .collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond, Long::sum));

        return new ResourceInfo(new AvailableCPU(cpus, usedCores), new AvailableMemory(memory, usedMemory));
    }

    private boolean ensureNodeResource(NodeInfo actual, NodeInfo requirement) {
        if (null == actual) {
            return false;
        }
        return actual.getAvailableCores().containsAll(requirement.getAvailableCores())
                && actual.getMemoryInMB() >= requirement.getMemoryInMB();
    }

}
