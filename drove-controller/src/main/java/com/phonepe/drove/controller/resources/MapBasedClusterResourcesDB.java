package com.phonepe.drove.controller.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.common.discovery.nodedata.ExecutorNodeData;
import com.phonepe.drove.common.model.ExecutorResourceSnapshot;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;
import com.phonepe.drove.models.application.requirements.ResourceRequirementVisitor;
import lombok.SneakyThrows;
import lombok.val;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 *
 */
public class MapBasedClusterResourcesDB implements ClusterResourcesDB {
    private final Map<String, ExecutorHostInfo> nodes = new ConcurrentHashMap<>();

    @Override
    @SneakyThrows
    public synchronized void update(List<ExecutorNodeData> nodeData) {
        nodes.putAll(nodeData.stream()
                             .map(node -> convertState(node.getState()))
                             .collect(Collectors.toUnmodifiableMap(ExecutorHostInfo::getExecutorId,
                                                                   Function.identity())));
        System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(nodes));
    }

    @Override
    @SneakyThrows
    public synchronized void update(final ExecutorResourceSnapshot snapshot) {
        nodes.put(snapshot.getExecutorId(), convertState(snapshot));
        System.out.println("updated data: " + new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(nodes));
    }

    @Override
    public synchronized List<AllocatedExecutorNode> selectNodes(
            List<ResourceRequirement> requirements, int instances, Predicate<AllocatedExecutorNode> filter) {
/*        nodes.values()
                .stream()
                .filter()*/
        return null;
    }

    private String id(final ExecutorHostInfo hostInfo) {
        return hostInfo.getExecutorId();
    }

    private boolean ensureResource(final ExecutorHostInfo hostInfo, final List<ResourceRequirement> resources) {
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
        val eligibleNodes = hostInfo.getNodes()
                .values()
                .stream()
                .filter(node -> freeCoresForNode(node) >= cpus && node.getMemory()
                        .getAvailable() >= memory)
                .collect(Collectors.toUnmodifiableList());

        return false;
    }

    public Map<Integer, ExecutorHostInfo.NumaNodeInfo> convertToNodeInfo(final ExecutorResourceSnapshot resourceSnapshot) {
        val numaNodes = new HashMap<Integer, ExecutorHostInfo.NumaNodeInfo>();
        val cpus = resourceSnapshot.getCpus();
        val memory = resourceSnapshot.getMemory();
        cpus.getFreeCores()
                .forEach((key, freeCores) -> {
                    val nodeInfo = numaNodes.computeIfAbsent(key, k -> new ExecutorHostInfo.NumaNodeInfo());
                    freeCores.forEach(i -> nodeInfo.getCores()
                            .put(i, ExecutorHostInfo.CoreState.FREE)); //TODO::CHECK STATE BEFORE CHANGING
                });
        cpus.getUsedCores()
                .forEach((key, usedCores) -> {
                    val nodeInfo = numaNodes.computeIfAbsent(key, k -> new ExecutorHostInfo.NumaNodeInfo());
                    usedCores.forEach(i -> nodeInfo.getCores()
                            .put(i, ExecutorHostInfo.CoreState.IN_USE));//TODO::CHECK STATE BEFORE CHANGING
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

    private ExecutorHostInfo convertState(final ExecutorResourceSnapshot snapshot) {
        return new ExecutorHostInfo(snapshot.getExecutorId(), convertToNodeInfo(snapshot));
    }
}
