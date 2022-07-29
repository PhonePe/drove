package com.phonepe.drove.models.info.nodedata;

import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExecutorNodeData extends NodeData {

    ExecutorResourceSnapshot state;
    List<InstanceInfo> instances;
    List<TaskInfo> tasks;
    Set<String> tags;
    boolean blacklisted;

    @Jacksonized
    @Builder
    @SuppressWarnings("java:S107")
    public ExecutorNodeData(
            String hostname,
            int port,
            NodeTransportType transportType,
            Date updated,
            ExecutorResourceSnapshot state,
            List<InstanceInfo> instances,
            List<TaskInfo> tasks,
            Set<String> tags,
            boolean blacklisted) {
        super(NodeType.EXECUTOR, hostname, port, transportType, updated);
        this.state = state;
        this.instances = instances;
        this.tasks = tasks;
        this.tags = tags;
        this.blacklisted = blacklisted;
    }

    @Override
    public <T> T accept(NodeDataVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public static ExecutorNodeData from(
            final ExecutorNodeData nodeData,
            final ExecutorResourceSnapshot currentState,
            final List<InstanceInfo> instances,
            final List<TaskInfo> taskInstances,
            final Set<String> tags, boolean blacklisted) {
        return new ExecutorNodeData(nodeData.getHostname(),
                                    nodeData.getPort(),
                                    nodeData.getTransportType(),
                                    new Date(),
                                    currentState,
                                    instances,
                                    taskInstances,
                                    null == tags ? Collections.emptySet() : tags,
                                    blacklisted);
    }
}
