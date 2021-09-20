package com.phonepe.drove.executor.resource;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.*;

/**
 *
 */
@Slf4j
public class ResourceDB {
    public enum ResourceLockType {
        SOFT,
        HARD
    }

    @Value
    public static class ResourceUsage {
        String id;
        ResourceLockType type;
        Map<Integer, Set<Integer>> cores;
        Map<Integer, Long> memory;
    }

    @Data
    @AllArgsConstructor
    public static class NodeInfo {
        private Set<Integer> availableCores;
        private long memoryInMB;
    }

    private Map<Integer, NodeInfo> nodes = Collections.emptyMap();
    private final Map<String, ResourceUsage> resourceLocks = new HashMap<>();

    public synchronized boolean lockResources(final ResourceUsage usage) {
        val hasCores = usage.getCores()
                .entrySet()
                .stream()
                .allMatch(entry -> nodes.containsKey(entry.getKey())
                        && nodes.get(entry.getKey())
                        .getAvailableCores()
                        .containsAll(entry.getValue()));
        if (!hasCores) {
            log.error("Provided cpu requirement not available. Usage Info: {}", usage);
            return false;
        }
        val hasMemory = usage.getMemory()
                .entrySet()
                .stream()
                .allMatch(entry -> nodes.containsKey(entry.getKey()) && nodes.get(entry.getKey())
                        .getMemoryInMB() >= entry.getValue());
        if (!hasMemory) {
            log.error("Provided memory requirement not available. Usage Info: {}", usage);
            return false;
        }
        val currNodes = new HashMap<>(nodes);
        usage.getCores()
                .forEach((node, usedCores) -> currNodes.computeIfPresent(node, (key, nodeInfo) -> {
                    nodeInfo.getAvailableCores().removeAll(usedCores);
                    return nodeInfo;
                }));
        usage.getMemory()
                .forEach((node, usedMemory) -> currNodes.computeIfPresent(node, (key, nodeInfo) -> {
                    nodeInfo.setMemoryInMB(nodeInfo.getMemoryInMB() - usedMemory);
                    return nodeInfo;
                }));
        nodes = Map.copyOf(currNodes);
        resourceLocks.put(usage.getId(), usage);
        return hasCores;
    }

    public synchronized void reclaimResources(String id) {
        val usage = resourceLocks.get(id);
        if (null == usage) {
            log.warn("No recorded usage for id: {}", id);
            return;
        }
        val currNodes = new HashMap<>(nodes);
        usage.getCores()
                .forEach((node, usedCores) -> currNodes.computeIfPresent(node, (key, nodeInfo) -> {
                    nodeInfo.getAvailableCores().addAll(usedCores);
                    return nodeInfo;
                }));
        usage.getMemory()
                .forEach((node, usedMemory) -> currNodes.computeIfPresent(node, (key, nodeInfo) -> {
                    nodeInfo.setMemoryInMB(nodeInfo.getMemoryInMB() + usedMemory);
                    return nodeInfo;
                }));
        nodes = Map.copyOf(currNodes);
        resourceLocks.remove(id);
    }
}
