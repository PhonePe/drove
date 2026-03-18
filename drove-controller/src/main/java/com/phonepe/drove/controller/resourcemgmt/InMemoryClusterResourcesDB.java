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

package com.phonepe.drove.controller.resourcemgmt;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.phonepe.drove.controller.resourcemgmt.allocator.GpuAwareNumaExecutorResourceAllocator;
import com.phonepe.drove.controller.resourcemgmt.selector.CompositeExecutorSelector;
import com.phonepe.drove.controller.resourcemgmt.selector.ExecutorSelector;
import com.phonepe.drove.controller.resourcemgmt.selector.FirstFitExecutorSelector;
import com.phonepe.drove.controller.resourcemgmt.selector.ScoredExecutorSelector;
import com.phonepe.drove.models.application.requirements.*;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.GPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.StampedLock;
import java.util.function.LongBinaryOperator;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 *
 */
@Singleton
@Slf4j
public class InMemoryClusterResourcesDB extends ClusterResourcesDB {
    private static final Duration MAX_REMOVED_NODE_RETENTION_WINDOW = Duration.ofDays(2);

    private final ExecutorSelector selector;
    private final Map<String, ExecutorHostInfo> nodes = new HashMap<>();

    @Inject
    public InMemoryClusterResourcesDB(ExecutorSelector selector) {
        this.selector = selector;
    }

    public InMemoryClusterResourcesDB() {
        this(new CompositeExecutorSelector(new FirstFitExecutorSelector(new GpuAwareNumaExecutorResourceAllocator()), new ScoredExecutorSelector(new GpuAwareNumaExecutorResourceAllocator())));
    }

    @VisibleForTesting
    InMemoryClusterResourcesDB(ExecutorSelector selector, Map<String, ExecutorHostInfo> nodes) {
        this.selector = selector;
        this.nodes.putAll(nodes);
    }

    private final Map<String, ExecutorHostInfo> removedNodes = new LinkedHashMap<>() {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ExecutorHostInfo> eldest) {
            val status = eldest.getValue()
                    .getNodeData()
                    .getUpdated()
                    .after(Date.from(Instant.now().plus(MAX_REMOVED_NODE_RETENTION_WINDOW)));
            if (status) {
                log.warn("Removed executor data for {} will be permanently deleted",
                         eldest.getValue().getExecutorId());
            }
            return status;
        }
    };

    private final Set<String> blackListedNodes = new HashSet<>();

    private final StampedLock lock = new StampedLock();

    @Override
    @MonitoredFunction
    public long executorCount(boolean skipOffDutyNodes) {
        val stamp = lock.readLock();
        try {
            return nodes.values()
                    .stream()
                    .filter(node -> checkOffDuty(skipOffDutyNodes, node)) //Else remove blacklisted
                    .count();
        }
        finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    @MonitoredFunction
    public List<ExecutorHostInfo> currentSnapshot(boolean skipOffDutyNodes) {
        val stamp = lock.readLock();
        try {
            return nodes.values()
                    .stream()
                    .filter(node -> checkOffDuty(skipOffDutyNodes, node)) //Else remove blacklisted
                    .toList();
        }
        finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    @MonitoredFunction
    public List<ExecutorHostInfo> lastKnownSnapshots() {
        val stamp = lock.readLock();
        try {
            return List.copyOf(removedNodes.values());
        }
        finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    @MonitoredFunction
    public Optional<ExecutorHostInfo> currentSnapshot(final String executorId) {
        val stamp = lock.readLock();
        try {
            return Optional.ofNullable(nodes.get(executorId));
        }
        finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public Optional<ExecutorHostInfo> lastKnownSnapshot(String executorId) {
        val stamp = lock.readLock();
        try {
            return Optional.ofNullable(removedNodes.get(executorId));
        }
        finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    @MonitoredFunction
    public void remove(Collection<String> executorIds) {
        val stamp = lock.writeLock();
        try {
            executorIds.stream()
                    .map(nodes::remove)
                    .filter(Objects::nonNull)
                    .forEach(removedExecutor -> {
                        removedNodes.put(removedExecutor.getExecutorId(), removedExecutor);
                        log.info("Executor {} is now out of the cluster", removedExecutor.getExecutorId());
                    });
            raiseEvent(Set.of(), Set.copyOf(executorIds), liveExecutorsUnsafe());
        }
        finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    @SneakyThrows
    @MonitoredFunction
    public void update(List<ExecutorNodeData> nodeData) {
        val stamp = lock.writeLock();
        try {
            val newNodes = Set.copyOf(Sets.difference(nodeData.stream()
                                                   .map(node -> node.getState().getExecutorId())
                                                   .collect(Collectors.toUnmodifiableSet()),
                                           nodes.keySet()));
            nodeData.forEach(this::updateNodeDataUnsafe);
            if (!newNodes.isEmpty()) {
                raiseEvent(newNodes, Set.of(), liveExecutorsUnsafe());
            }
        }
        finally {
            lock.unlockWrite(stamp);
        }
    }


    @Override
    @SneakyThrows
    @MonitoredFunction
    public void update(final ExecutorResourceSnapshot snapshot) {
        val stamp = lock.writeLock();
        try {
            val node = nodes.get(snapshot.getExecutorId());
            if (null != node) {
                updateNodeDataUnsafe(convertState(node, snapshot));
            }
        }
        finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    @SneakyThrows
    @MonitoredFunction
    public Optional<AllocatedExecutorNode> selectNodes(
            List<ResourceRequirement> requirements,
            Set<ExecutorState> allowedExecutorState,
            Predicate<AllocatedExecutorNode> filter) {
        return selectNodes(requirements, allowedExecutorState, filter, true);
    }

    @VisibleForTesting
    public Optional<AllocatedExecutorNode> selectNodes(
            List<ResourceRequirement> requirements,
            Set<ExecutorState> allowedExecutorState,
            Predicate<AllocatedExecutorNode> filter,
            boolean shouldShuffle) {
        val stamp = lock.writeLock();
        try {
            val rawNodes = new ArrayList<>(nodes.values());
            if(shouldShuffle) {
                Collections.shuffle(rawNodes);
            }
            val selectedNode = selector.select(rawNodes,
                    node -> inRequiredState(node, allowedExecutorState),
                    requirements,
                    filter);
            selectedNode.ifPresent(this::softLockResources);
            return selectedNode;
        }
        finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    @MonitoredFunction
    public void deselectNode(
            String executorId,
            CPUAllocation cpuAllocation,
            MemoryAllocation memoryAllocation,
            GPUAllocation gpuAllocation) {
        val stamp = lock.writeLock();
        try {
            softUnlockResources(executorId, cpuAllocation, memoryAllocation, gpuAllocation);
        }
        finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    @MonitoredFunction
    public boolean isBlacklisted(String executorId) {
        val stamp = lock.readLock();
        try {
            return isBlackListedInternal(executorId);
        }
        finally {
            lock.unlockRead(stamp);
        }
    }


    @MonitoredFunction
    @Override
    public boolean isActive(String executorId) {
        val stamp = lock.readLock();
        try {
            return isActiveInternal(executorId);
        }
        finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    @MonitoredFunction
    public void markBlacklisted(String executorId) {
        val stamp = lock.writeLock();
        try {
            blackListedNodes.add(executorId);
            raiseEvent(Set.of(), Set.of(executorId), liveExecutorsUnsafe());
        }
        finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    @MonitoredFunction
    public void unmarkBlacklisted(String executorId) {
        val stamp = lock.writeLock();
        try {
            blackListedNodes.remove(executorId);
            raiseEvent(Set.of(executorId), Set.of(), liveExecutorsUnsafe());
        }
        finally {
            lock.unlockWrite(stamp);
        }
    }

    private void updateNodeDataUnsafe(ExecutorNodeData rawNodeData) {
        val node = convertState(rawNodeData);
        updateNodeDataUnsafe(node);
    }

    private void updateNodeDataUnsafe(ExecutorHostInfo node) {
        val updated = nodes.compute(node.getExecutorId(), (eId, existing) -> {
            if (null != existing
                    && existing.getNodeData().getUpdated().after(node.getNodeData().getUpdated())) {
                log.warn(
                        "Ignored stale update for executor {} as existing data is from {} while update is " +
                                "from {}",
                        node.getExecutorId(),
                        existing.getNodeData().getUpdated().getTime(),
                        node.getNodeData().getUpdated().getTime());
                return existing;
            }
            return node;
        });
        if (null != removedNodes.remove(updated.getExecutorId())) {
            log.info("Executor {} is back in the cluster", updated.getExecutorId());
        }
    }

    private boolean isBlackListedInternal(String executorId) {
        // Consider nodes that are not locally blacklisted and ensure they are not in BLACKLISTED state
        return Optional.ofNullable(nodes.get(executorId))
                .map(node -> ExecutorState.BLACKLISTED.equals(node.getNodeData().getExecutorState()))
                .orElse(false)
                || blackListedNodes.contains(executorId);
    }

    private boolean inRequiredState(ExecutorHostInfo node, Set<ExecutorState> allowedStates) {
        if (blackListedNodes.contains(node.getExecutorId())) {
            // If node is blacklisted locally, only allow if BLACKLISTED state is allowed
            return allowedStates.contains(ExecutorState.BLACKLISTED);
        }
        // Else check actual state and ensure it's in allowed states
        return allowedStates.contains(node.nodeData.getExecutorState());
    }

    private boolean isActiveInternal(String executorId) {
        // Ensure node is not blacklisted locally and then check if it is in active state
        return Optional.ofNullable(nodes.get(executorId))
                .filter(node -> !blackListedNodes.contains(node.getExecutorId()))
                .map(node -> ExecutorState.ACTIVE.equals(node.getNodeData().getExecutorState()))
                .orElse(false);
    }

    private void softLockResources(AllocatedExecutorNode node) {
        updateResources(node.getExecutorId(),
                        node.getCpu(),
                        node.getMemory(),
                        ExecutorHostInfo.CoreState.ALLOCATED,
                        node.getGpu(),
                        ExecutorHostInfo.CoreState.ALLOCATED,
                (av, alloc) -> av - alloc);
    }

    private void softUnlockResources(
            String executorId,
            CPUAllocation cpuAllocation,
            MemoryAllocation memoryAllocation,
            GPUAllocation gpuAllocation) {
        updateResources(executorId,
                        cpuAllocation,
                        memoryAllocation,
                        ExecutorHostInfo.CoreState.FREE,
                        gpuAllocation,
                        ExecutorHostInfo.CoreState.FREE,
                        Long::sum);
    }

    private void updateResources(
            String executorId,
            CPUAllocation cpuAllocation,
            MemoryAllocation memoryAllocation,
            ExecutorHostInfo.CoreState newState,
            GPUAllocation gpuAllocation,
            ExecutorHostInfo.CoreState gpuNewState,
            LongBinaryOperator memUpdater) {
        cpuAllocation
                .getCores()
                .forEach((numaNodeId, coreIds) -> nodes.get(executorId)
                        .getNodes()
                        .entrySet()
                        .stream()
                        .filter(e -> Objects.equals(e.getKey(), numaNodeId))
                        .forEach(e -> coreIds.forEach(coreId -> e.getValue().getCores()
                                .put(coreId, newState))));
        memoryAllocation
                .getMemoryInMB()
                .forEach((numaNodeId, allocMem) -> nodes.get(executorId)
                        .getNodes()
                        .entrySet()
                        .stream()
                        .filter(e -> Objects.equals(e.getKey(), numaNodeId))
                        .forEach(e -> {
                            val memInfo = e.getValue().getMemory();
                            memInfo.setAvailable(memUpdater.applyAsLong(memInfo.getAvailable(), allocMem));
                        }));
        gpuAllocation.getGpuIds().forEach(gpuId -> {
            val gpuInfo = nodes.get(executorId).getGpuInfo();
            gpuInfo.getGpus().put(gpuId,  gpuNewState);
        });
    }



    private ExecutorHostInfo convertState(final ExecutorNodeData node) {
        val snapshot = node.getState();
        return new ExecutorHostInfo(snapshot.getExecutorId(), node, convertToNodeInfo(snapshot), convertToGpuInfo(snapshot));
    }

    private ExecutorHostInfo convertState(final ExecutorHostInfo node, final ExecutorResourceSnapshot snapshot) {
        val nodeData = ExecutorNodeData.from(node.getNodeData(),
                                             snapshot,
                                             node.getNodeData().getInstances(),
                                             node.getNodeData().getTasks(),
                                             node.getNodeData().getServiceInstances(),
                                             node.getNodeData().getTags(),
                                             node.getNodeData().getExecutorState(),
                                             node.getNodeData().getMetadata());
        return new ExecutorHostInfo(snapshot.getExecutorId(), nodeData, convertToNodeInfo(snapshot), convertToGpuInfo(snapshot));
    }

    private ExecutorHostInfo.HostGPUInfo convertToGpuInfo(ExecutorResourceSnapshot resourceSnapshot) {
        val gpuInfo = new ExecutorHostInfo.HostGPUInfo();
        if(resourceSnapshot.getGpus() == null) {
            return gpuInfo;
        }
        resourceSnapshot.getGpus().getFreeGpus().forEach(gpuId -> gpuInfo.getGpus().put(gpuId, ExecutorHostInfo.CoreState.FREE));
        resourceSnapshot.getGpus().getUsedGpus().forEach(gpuId -> gpuInfo.getGpus().put(gpuId, ExecutorHostInfo.CoreState.IN_USE));
        return gpuInfo;
    }

    /**
     * Convert ExecutorResourceSnapshot into NUMA node information map
     */
    private Map<Integer, ExecutorHostInfo.NumaNodeInfo> convertToNodeInfo(final ExecutorResourceSnapshot resourceSnapshot) {
        val numaNodes = new HashMap<Integer, ExecutorHostInfo.NumaNodeInfo>();
        val cpus = resourceSnapshot.getCpus();
        val memory = resourceSnapshot.getMemory();

        cpus.getFreeCores()
                .forEach((key, freeCores) -> {
                    val nodeInfo = numaNodes.computeIfAbsent(key, k -> new ExecutorHostInfo.NumaNodeInfo());
                    freeCores.forEach(i -> nodeInfo.getCores()
                            .compute(i,
                                     (core, state) -> state != ExecutorHostInfo.CoreState.ALLOCATED
                                                      ? ExecutorHostInfo.CoreState.FREE
                                                      : state));
                });
        cpus.getUsedCores()
                .forEach((key, usedCores) -> {
                    val nodeInfo = numaNodes.computeIfAbsent(key, k -> new ExecutorHostInfo.NumaNodeInfo());
                    usedCores.forEach(i -> nodeInfo.getCores()
                            .put(i, ExecutorHostInfo.CoreState.IN_USE));
                });
        memory.getUsedMemory()
                .forEach((key, usedMemory) -> {
                    val nodeInfo = numaNodes.computeIfAbsent(key, k -> new ExecutorHostInfo.NumaNodeInfo());
                    nodeInfo.getMemory().setUsed(usedMemory);
                });
        memory.getFreeMemory()
                .forEach((key, freeMemory) -> {
                    val nodeInfo = numaNodes.computeIfAbsent(key, k -> new ExecutorHostInfo.NumaNodeInfo());
                    nodeInfo.getMemory().setAvailable(freeMemory);
                });
        return numaNodes;
    }


    private Set<String> liveExecutorsUnsafe() {
        // Remove nodes that have been blacklisted locally as well as those that are not in ACTIVE state
        // ie maybe marked as blacklisted before or removed
        final var outOfRotationNodes = nodes.values()
                .stream()
                .filter(node -> !blackListedNodes.contains(node.getExecutorId()))
                .filter(entry -> !ExecutorState.ACTIVE.equals(entry.getNodeData().getExecutorState()))
                .map(ExecutorHostInfo::getExecutorId)
                .collect(Collectors.toSet());
        return Set.copyOf(Sets.difference(nodes.keySet(), outOfRotationNodes));
    }

    private boolean checkOffDuty(boolean skipOffDutyNodes, ExecutorHostInfo node) {
        return !skipOffDutyNodes //If off duty nodes are needed, return everything
                || !isBlackListedInternal(node.getExecutorId());
    }
}
