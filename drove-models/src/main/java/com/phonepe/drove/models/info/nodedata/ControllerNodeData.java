package com.phonepe.drove.models.info.nodedata;

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
public class ControllerNodeData extends NodeData {
    boolean leader;

    public ControllerNodeData(String hostname, int port, Date updated, boolean leader) {
        super(NodeType.CONTROLLER, hostname, port, updated);
        this.leader = leader;
    }


    @Override
    public <T> T accept(NodeDataVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public static ControllerNodeData from(final ControllerNodeData nodeData, boolean leader) {
        return new ControllerNodeData(nodeData.getHostname(), nodeData.getPort(), new Date(), leader);
    }
}
