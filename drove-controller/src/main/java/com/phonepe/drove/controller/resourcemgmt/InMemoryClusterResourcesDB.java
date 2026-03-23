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

import com.google.common.collect.Sets;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;
import com.phonepe.drove.models.application.requirements.ResourceRequirementVisitor;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

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

    private final Map<String, ExecutorHostInfo> nodes = new HashMap<>();
    private final Map<String, ExecutorHostInfo> removedNodes = new LinkedHashMap<>() {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ExecutorHostInfo> eldest) {
            val status = eldest.getValue()
                    .getNodeData()
                    .getUpdated()
                    .before(Date.from(Instant.now().minus(MAX_REMOVED_NODE_RETENTION_WINDOW)));
            if (status) {
                log.warn("Removed executor data for {} will be permanently deleted",
                         eldest.getValue().getExecutorId());
            }
            return status;
        }
    };

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
            val actuallyRemoved = executorIds.stream()
                    .map(nodes::remove)
                    .filter(Objects::nonNull)
                    .map(removedExecutor -> {
                        removedNodes.put(removedExecutor.getExecutorId(), removedExecutor);
                        log.debug("Executor {} is now out of the cluster", removedExecutor.getExecutorId());
                        return removedExecutor.getExecutorId();
                    })
            .toList();
            if(!actuallyRemoved.isEmpty()) {
                log.info("Executors {} are removed from the cluster", actuallyRemoved);
                raiseEvent(Set.of(), Set.copyOf(actuallyRemoved), liveExecutorsUnsafe());
            }
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
            val existingExecutorIds = liveExecutorsUnsafe();
            log.debug("Existing IDs: {}", existingExecutorIds);
            nodeData.forEach(this::updateExecutorNodeDataUnsafe);
            val liveExecutorIds = liveExecutorsUnsafe();
            val newNodes = Sets.difference(liveExecutorIds, existingExecutorIds);
            val missingNodes = Sets.difference(existingExecutorIds, liveExecutorIds);
            log.debug("After update, before update: {} live executors are {}, new executors are {}, removed executors are {}",
                    existingExecutorIds, liveExecutorIds, newNodes, missingNodes);
            if (!newNodes.isEmpty() || !missingNodes.isEmpty()) {
                log.info("Cluster topology changed. New executors: {}, out-of-rotation executors: {}",
                        newNodes, missingNodes);
                raiseEvent(Set.copyOf(newNodes), Set.copyOf(missingNodes), liveExecutorIds);
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
                val reAdded = updateExecutorHostInfoUnsafe(updateSnapshotInNode(node, snapshot));
                if(reAdded) {
                    raiseEvent(Set.of(snapshot.getExecutorId()), Set.of(), liveExecutorsUnsafe());
                }
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
        val stamp = lock.writeLock();
        try {
            val rawNodes = new ArrayList<>(nodes.values());
            Collections.shuffle(rawNodes);
            return rawNodes
                    .stream()
                    .filter(node -> inRequiredState(node, allowedExecutorState))
                    .map(node -> ensureResource(node, requirements))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(filter)
                    .peek(this::softLockResources)
                    .findFirst();
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
            MemoryAllocation memoryAllocation) {
        val stamp = lock.writeLock();
        try {
            softUnlockResources(executorId, cpuAllocation, memoryAllocation);
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
            return Optional.ofNullable(nodes.get(executorId))
                .map(InMemoryClusterResourcesDB::isBlackListedInternal)
                .orElse(false);
        }
        finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    @MonitoredFunction
    public Set<String> blacklistedNodes() {
        val stamp = lock.readLock();
        try {
            return nodes.values()
                    .stream()
                    .filter(InMemoryClusterResourcesDB::isBlackListedInternal)
                    .map(ExecutorHostInfo::getExecutorId)
                    .collect(Collectors.toUnmodifiableSet());
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
            return Optional.ofNullable(nodes.get(executorId))
                    .map(InMemoryClusterResourcesDB::isActiveInternal)
                    .orElse(false);
        }
        finally {
            lock.unlockRead(stamp);
        }
    }

    private static boolean inRequiredState(ExecutorHostInfo node, Set<ExecutorState> allowedStates) {
        return allowedStates.contains(node.nodeData.getExecutorState());
    }

    private static Optional<AllocatedExecutorNode> ensureResource(
            final ExecutorHostInfo hostInfo,
            final List<ResourceRequirement> resources) {
        val cpus =
                resources.stream()
                        .mapToLong(req -> req.accept(new ResourceRequirementVisitor<>() {

                            @Override
                            public Long visit(CPURequirement cpuRequirement) {
                                return cpuRequirement.getCount();
                            }

                            @Override
                            public Long visit(MemoryRequirement memoryRequirement) {
                                return 0L;
                            }
                        }))
                        .sum();
        val memory =
                resources.stream()
                        .mapToLong(req -> req.accept(new ResourceRequirementVisitor<>() {

                            @Override
                            public Long visit(CPURequirement cpuRequirement) {
                                return 0L;
                            }

                            @Override
                            public Long visit(MemoryRequirement memoryRequirement) {
                                return memoryRequirement.getSizeInMB();
                            }
                        }))
                        .sum();

        //NOTE: THis ensures everything is on the SAME numa node for performance
        return hostInfo.getNodes()
                .entrySet()
                .stream()
                .filter(entry -> freeCoresForNode(entry.getValue()) >= cpus && entry.getValue().getMemory()
                        .getAvailable() >= memory)
                .map(node -> new AllocatedExecutorNode(hostInfo.getExecutorId(),
                                                       hostInfo.getNodeData().getHostname(),
                                                       hostInfo.getNodeData().getPort(),
                                                       hostInfo.getNodeData().getTransportType(),
                                                       allocateCPUs(node, cpus),
                                                       new MemoryAllocation(
                                                               Collections.singletonMap(node.getKey(), memory)),
                                                       Objects.requireNonNullElse(hostInfo.getNodeData().getTags(),
                                                                                  Set.of()),
                                                       Objects.requireNonNullElse(hostInfo.getNodeData().getMetadata(),
                                                                                  Map.of()),
                                                       hostInfo.getNodeData().getExecutorState()))
                .findAny();
    }

    /**
     * Allocate free CPUs as per requirement
     *
     * @param nodeInfo     current node
     * @param requiredCPUs number of CPUs needed
     * @return Set of allocated CPU cores on same numa node
     */
    private static CPUAllocation allocateCPUs(
            final Map.Entry<Integer, ExecutorHostInfo.NumaNodeInfo> nodeInfo,
            long requiredCPUs) {
        return new CPUAllocation(Map.of(nodeInfo.getKey(),
                                        nodeInfo.getValue()
                                                .getCores()
                                                .entrySet()
                                                .stream()
                                                .filter(entry -> entry.getValue()
                                                        .equals(ExecutorHostInfo.CoreState.FREE))
                                                .map(Map.Entry::getKey)
                                                .limit(requiredCPUs)
                                                .collect(Collectors.toUnmodifiableSet())));
    }

    private static Map<Integer, ExecutorHostInfo.NumaNodeInfo> convertToNodeInfo(final ExecutorResourceSnapshot resourceSnapshot) {
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

    private static long freeCoresForNode(final ExecutorHostInfo.NumaNodeInfo node) {
        return node.getCores()
                .entrySet()
                .stream()
                .filter(e -> e.getValue()
                        .equals(ExecutorHostInfo.CoreState.FREE)).count();
    }

    private static ExecutorHostInfo toHostInfo(final ExecutorNodeData node) {
        val snapshot = node.getState();
        return new ExecutorHostInfo(snapshot.getExecutorId(), node, convertToNodeInfo(snapshot));
    }

    private static ExecutorHostInfo updateSnapshotInNode(
            final ExecutorHostInfo node,
            final ExecutorResourceSnapshot snapshot) {
        return new ExecutorHostInfo(snapshot.getExecutorId(), node.getNodeData(), convertToNodeInfo(snapshot));
    }

    private static boolean checkOffDuty(boolean skipOffDutyNodes, ExecutorHostInfo node) {
        return !skipOffDutyNodes //If off duty nodes are needed, return everything
                || isActiveInternal(node);
    }

    private static boolean isActiveInternal(ExecutorHostInfo node) {
        return ExecutorState.ACTIVE.equals(node.getNodeData().getExecutorState());
    }

    private static boolean isBlackListedInternal(ExecutorHostInfo node) {
        return node.getNodeData().getExecutorState().isBlacklisted();
    }

    private Set<String> liveExecutorsUnsafe() {
        // Remove nodes that have been blacklisted locally as well as those that are not in ACTIVE state
        // ie maybe marked as blacklisted before or removed
        return nodes.values()
                .stream()
                .filter(entry -> ExecutorState.ACTIVE.equals(entry.getNodeData().getExecutorState()))
                .map(ExecutorHostInfo::getExecutorId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void softLockResources(AllocatedExecutorNode node) {
        updateResources(node.getExecutorId(),
                        node.getCpu(),
                        node.getMemory(),
                        ExecutorHostInfo.CoreState.ALLOCATED,
                        (av, alloc) -> av - alloc);
    }

    private void softUnlockResources(
            String executorId,
            CPUAllocation cpuAllocation,
            MemoryAllocation memoryAllocation) {
        updateResources(executorId,
                        cpuAllocation,
                        memoryAllocation,
                        ExecutorHostInfo.CoreState.FREE,
                        Long::sum);
    }

    private void updateExecutorNodeDataUnsafe(ExecutorNodeData rawNodeData) {
        log.debug("Updating node data for executor {}", rawNodeData.getState().getExecutorId());
        val node = toHostInfo(rawNodeData);
        updateExecutorHostInfoUnsafe(node);
    }

    private boolean updateExecutorHostInfoUnsafe(ExecutorHostInfo node) {
        log.debug("Updating host info for executor {}", node.getExecutorId());
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
            return true;
        }
        return false;
    }

    private void updateResources(
            String executorId,
            CPUAllocation cpuAllocation,
            MemoryAllocation memoryAllocation,
            ExecutorHostInfo.CoreState newState,
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
    }

 }
