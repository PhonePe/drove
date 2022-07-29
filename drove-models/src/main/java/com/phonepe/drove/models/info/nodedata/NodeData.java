package com.phonepe.drove.models.info.nodedata;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

import java.util.Date;

/**
 *
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "CONTROLLER", value = ControllerNodeData.class),
        @JsonSubTypes.Type(name = "EXECUTOR", value = ExecutorNodeData.class),
})
@Data
public abstract class NodeData {
    private final NodeType type;
    private final String hostname;
    private final int port;
    private final NodeTransportType transportType;
    private final Date updated;
    
    public abstract <T> T accept(final NodeDataVisitor<T> visitor);
}
