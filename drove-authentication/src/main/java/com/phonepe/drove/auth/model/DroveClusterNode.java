package com.phonepe.drove.auth.model;

import com.phonepe.drove.models.info.nodedata.NodeType;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 * Represents an authenticated cluster controller node
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DroveClusterNode extends DroveUser {
    NodeType nodeType;

    public DroveClusterNode(String id, NodeType nodeType) {
        super(DroveUserType.CLUSTER_NODE, id, DroveUserRole.CLUSTER_NODE);
        this.nodeType = nodeType;
    }

    @Override
    public <T> T accept(DroveUserVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
