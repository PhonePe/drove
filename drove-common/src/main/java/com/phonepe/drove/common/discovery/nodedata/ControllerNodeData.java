package com.phonepe.drove.common.discovery.nodedata;

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
    boolean isLeader;

    public ControllerNodeData(String hostname, int port, Date updated, boolean isLeader) {
        super(NodeType.CONTROLLER, hostname, port, updated);
        this.isLeader = isLeader;
    }


    @Override
    public <T> T accept(NodeDataVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
