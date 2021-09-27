package com.phonepe.drove.common.discovery.nodedata;

import com.phonepe.drove.common.model.ExecutorState;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import java.util.Date;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExecutorNodeData extends NodeData {

    ExecutorState state;

    public ExecutorNodeData(String hostname, int port, Date updated, ExecutorState state) {
        super(NodeType.EXECUTOR, hostname, port, updated);
        this.state = state;
    }

    @Override
    public <T> T accept(NodeDataVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public static ExecutorNodeData from(final ExecutorNodeData nodeData, final ExecutorState currentState) {
        return new ExecutorNodeData(nodeData.getHostname(), nodeData.getPort(), new Date(), currentState);
    }
}
