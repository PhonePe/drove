package com.phonepe.drove.controller.resourcemgmt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.common.discovery.nodedata.ExecutorNodeData;
import com.phonepe.drove.common.model.ExecutorResourceSnapshot;
import com.phonepe.drove.common.model.resources.allocation.CPUAllocation;
import com.phonepe.drove.common.model.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;
import com.phonepe.drove.models.application.requirements.ResourceRequirementVisitor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongBinaryOperator;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 *
 */
@Singleton
@Slf4j
public class MapBasedClusterResourcesDB implements ClusterResourcesDB {
    private final Map<String, ExecutorHostInfo> nodes = new ConcurrentHashMap<>();

    @Override
    public Optional<ExecutorHostInfo> currentSnapshot(final String executorId) {
        return Optional.ofNullable(nodes.get(executorId));
    }

    @Override
    public synchronized void remove(Collection<String> executorIds) {
        executorIds.forEach(nodes::remove);
    }

    @Override
    @SneakyThrows
    public synchronized void update(List<ExecutorNodeData> nodeData) {
        nodes.putAll(nodeData.stream()
                             .map(this::convertState)
                             .collect(Collectors.toUnmodifiableMap(ExecutorHostInfo::getExecutorId,
                                                                   Function.identity())));
        log.info("Snapshot: {}", new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(nodes));
    }

    @Override
    @SneakyThrows
    public synchronized void update(final ExecutorResourceSnapshot snapshot) {
        nodes.computeIfPresent(snapshot.getExecutorId(), (executorId, node) -> convertState(node, snapshot));
        log.info("Snapshot: {}", new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(nodes));
    }

    @Override
    @SneakyThrows
    public synchronized List<AllocatedExecutorNode> selectNodes(
            List<ResourceRequirement> requirements, int instances, Predicate<AllocatedExecutorNode> filter) {
        val allocNodes = nodes.values()
                .stream()
                .map(node -> ensureResource(node, requirements))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(filter)
                .peek(this::softLockResources)
                .collect(Collectors.toUnmodifiableList());
        log.info("Snapshot: {}", new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(nodes));
        return allocNodes;
    }

    @Override
    public synchronized void deselectNode(AllocatedExecutorNode executorNode) {
        softUnlockResources(executorNode);
    }

    private void softLockResources(AllocatedExecutorNode node) {
        updateResources(node, ExecutorHostInfo.CoreState.ALLOCATED, (av, alloc) -> av - alloc );
    }

    private void softUnlockResources(AllocatedExecutorNode node) {
        updateResources(node, ExecutorHostInfo.CoreState.FREE, Long::sum);
    }

    private void updateResources(AllocatedExecutorNode node, ExecutorHostInfo.CoreState newState, LongBinaryOperator memUpdater) {
        node.getCpu()
                .getCores()
                .forEach((numaNodeId, coreIds) -> nodes.get(node.getExecutorId())
                        .getNodes()
                        .entrySet()
                        .stream()
                        .filter(e -> Objects.equals(e.getKey(), numaNodeId))
                        .forEach(e -> coreIds.forEach(coreId -> e.getValue().getCores()
                                .put(coreId, newState))));
        node.getMemory()
                .getMemoryInMB()
                .forEach((numaNodeId, allocMem) -> nodes.get(node.getExecutorId())
                        .getNodes()
                        .entrySet()
                        .stream()
                        .filter(e -> Objects.equals(e.getKey(), numaNodeId))
                        .forEach(e -> {
                            val memInfo = e.getValue().getMemory();
                            memInfo.setAvailable(memUpdater.applyAsLong(memInfo.getAvailable(), allocMem));
                        }));
    }

    private Optional<AllocatedExecutorNode> ensureResource(
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
                                                       allocateCPUs(node, cpus),
                                                       new MemoryAllocation(Collections.singletonMap(node.getKey(),
                                                                                                     memory))))
                .findAny();
    }

    /**
     * Allocate free CPUs as per requirement
     *
     * @param nodeInfo     current node
     * @param requiredCPUs number of CPUs needed
     * @return Set of allocated CPU cores on same numa node
     */
    private CPUAllocation allocateCPUs(
            final Map.Entry<Integer, ExecutorHostInfo.NumaNodeInfo> nodeInfo,
            long requiredCPUs) {
        return new CPUAllocation(Collections.singletonMap(nodeInfo.getKey(), nodeInfo.getValue()
                                                                  .getCores()
                                                                  .entrySet()
                                                                  .stream()
                                                                  .filter(entry -> entry.getValue().equals(ExecutorHostInfo.CoreState.FREE))
                                                                  .map(Map.Entry::getKey)
                                                                  .limit(requiredCPUs)
                                                                  .collect(Collectors.toUnmodifiableSet())
                                                         ));
    }

    public Map<Integer, ExecutorHostInfo.NumaNodeInfo> convertToNodeInfo(final ExecutorResourceSnapshot resourceSnapshot) {
        val numaNodes = new HashMap<Integer, ExecutorHostInfo.NumaNodeInfo>();
        val cpus = resourceSnapshot.getCpus();
        val memory = resourceSnapshot.getMemory();
        cpus.getFreeCores()
                .forEach((key, freeCores) -> {
                    val nodeInfo = numaNodes.computeIfAbsent(key, k -> new ExecutorHostInfo.NumaNodeInfo());
                    freeCores.forEach(i -> nodeInfo.getCores()
                            .compute(i, (core, state) -> null == state || state != ExecutorHostInfo.CoreState.ALLOCATED ? ExecutorHostInfo.CoreState.FREE : state));
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

    private long freeCoresForNode(ExecutorHostInfo.NumaNodeInfo node) {
        return node.getCores()
                .entrySet()
                .stream()
                .filter(e -> e.getValue()
                        .equals(ExecutorHostInfo.CoreState.FREE)).count();
    }

    private ExecutorHostInfo convertState(final ExecutorNodeData node) {
        val snapshot = node.getState();
        return new ExecutorHostInfo(snapshot.getExecutorId(), node, convertToNodeInfo(snapshot));
    }

    private ExecutorHostInfo convertState(final ExecutorHostInfo node, final ExecutorResourceSnapshot snapshot) {
        return new ExecutorHostInfo(snapshot.getExecutorId(), node.getNodeData(), convertToNodeInfo(snapshot));
    }
}
