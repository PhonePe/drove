package com.phonepe.drove.common.discovery.nodedata;

import com.phonepe.drove.common.model.ExecutorResourceSnapshot;
import com.phonepe.drove.models.instance.InstanceInfo;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Date;
import java.util.List;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExecutorNodeData extends NodeData {

    ExecutorResourceSnapshot state;
    List<InstanceInfo> instances;

    @Jacksonized
    @Builder
    public ExecutorNodeData(
            String hostname,
            int port,
            Date updated,
            ExecutorResourceSnapshot state,
            List<InstanceInfo> instances) {
        super(NodeType.EXECUTOR, hostname, port, updated);
        this.state = state;
        this.instances = instances;
    }

    @Override
    public <T> T accept(NodeDataVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public static ExecutorNodeData from(
            final ExecutorNodeData nodeData,
            final ExecutorResourceSnapshot currentState,
            final List<InstanceInfo> instances) {
        return new ExecutorNodeData(nodeData.getHostname(), nodeData.getPort(), new Date(), currentState, instances);
    }
}
