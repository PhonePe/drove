package com.phonepe.drove.executor.resource;

import com.google.common.collect.Sets;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import java.util.*;

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
    }


    private boolean ensureNodeResource(NodeInfo actual, NodeInfo requirement) {
        if (null == actual) {
            return false;
        }
        return actual.getAvailableCores().containsAll(requirement.getAvailableCores())
                && actual.getMemoryInMB() >= requirement.getMemoryInMB();
    }

}
