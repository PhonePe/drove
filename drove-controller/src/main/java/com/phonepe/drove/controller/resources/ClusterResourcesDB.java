package com.phonepe.drove.controller.resources;

import com.phonepe.drove.common.discovery.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;

import java.util.List;
import java.util.function.Function;

/**
 *
 */
public interface ClusterResourcesDB {
    void update(final List<ExecutorNodeData> nodeData);

    List<AllocatedExecutorNode> selectNodes(
            List<ResourceRequirement> requirements,
            int instances,
            Function<AllocatedExecutorNode, Boolean> filter);
}
