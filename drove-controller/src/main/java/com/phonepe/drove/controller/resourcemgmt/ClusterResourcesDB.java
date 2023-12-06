package com.phonepe.drove.controller.resourcemgmt;

import com.phonepe.drove.models.application.requirements.ResourceRequirement;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import io.appform.functionmetrics.MonitoredFunction;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 *
 */
public interface ClusterResourcesDB {
    List<ExecutorHostInfo> currentSnapshot(boolean skipOffDutyNodes);

    @MonitoredFunction
    List<ExecutorHostInfo> lastKnownSnapshots();

    Optional<ExecutorHostInfo> currentSnapshot(String executorId);

    Optional<ExecutorHostInfo> lastKnownSnapshot(String executorId);

    void remove(Collection<String> executorIds);

    void update(final List<ExecutorNodeData> nodeData);

    void update(ExecutorResourceSnapshot snapshot);

    Optional<AllocatedExecutorNode> selectNodes(
            List<ResourceRequirement> requirements, Predicate<AllocatedExecutorNode> filter);

    void deselectNode(final AllocatedExecutorNode executorNode);

    boolean isBlacklisted(String executorId);

    void markBlacklisted(String executorId);

    void unmarkBlacklisted(String executorId);

}
