package com.phonepe.drove.controller.resources;

import com.phonepe.drove.common.discovery.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;
import com.phonepe.drove.models.application.requirements.ResourceRequirementVisitor;
import lombok.val;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 */
public class MapBasedClusterResourcesDB implements ClusterResourcesDB {
    private final Map<String, ExecutorHostInfo> nodes = new ConcurrentHashMap<>();

    @Override
    public synchronized void update(List<ExecutorNodeData> nodeData) {
//        nodes.putAll(nodeData.stream().collect(Collectors.toUnmodifiableMap(this::id, Function.identity())));
    }

    @Override
    public synchronized List<ExecutorNode> selectNodes(
            List<ResourceRequirement> requirements, int instances, Function<ExecutorNode, Boolean> filter) {
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
                .filter(node -> node.getCores().size() >= cpus && node.getMemory().getAvailable() >= memory)
                .collect(Collectors.toUnmodifiableList());

        return false;
    }
}
