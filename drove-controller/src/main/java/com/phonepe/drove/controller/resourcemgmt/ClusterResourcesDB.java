package com.phonepe.drove.controller.resourcemgmt;

import com.phonepe.drove.common.discovery.nodedata.ExecutorNodeData;
import com.phonepe.drove.common.model.ExecutorResourceSnapshot;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 *
 */
public interface ClusterResourcesDB {
    List<ExecutorHostInfo> currentSnapshot();

    Optional<ExecutorHostInfo> currentSnapshot(String executorId);

    void remove(Collection<String> executorIds);

    void update(final List<ExecutorNodeData> nodeData);

    void update(ExecutorResourceSnapshot snapshot);

    List<AllocatedExecutorNode> selectNodes(
            List<ResourceRequirement> requirements,
            int instances,
            Predicate<AllocatedExecutorNode> filter);
    void deselectNode(final AllocatedExecutorNode executorNode);
}
