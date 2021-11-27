package com.phonepe.drove.models.info.nodedata;

import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.instance.InstanceInfo;
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
    Set<String> tags;

    @Jacksonized
    @Builder
    public ExecutorNodeData(
            String hostname,
            int port,
            Date updated,
            ExecutorResourceSnapshot state,
            List<InstanceInfo> instances, Set<String> tags) {
        super(NodeType.EXECUTOR, hostname, port, updated);
        this.state = state;
        this.instances = instances;
        this.tags = tags;
    }

    @Override
    public <T> T accept(NodeDataVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public static ExecutorNodeData from(
            final ExecutorNodeData nodeData,
            final ExecutorResourceSnapshot currentState,
            final List<InstanceInfo> instances,
            final Set<String> tags) {
        return new ExecutorNodeData(nodeData.getHostname(),
                                    nodeData.getPort(),
                                    new Date(),
                                    currentState,
                                    instances,
                                    null == tags ? Collections.emptySet() : tags);
    }
}
