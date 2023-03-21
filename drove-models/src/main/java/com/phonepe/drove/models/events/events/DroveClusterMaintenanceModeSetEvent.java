package com.phonepe.drove.models.events.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.phonepe.drove.models.events.DroveEventType;
import com.phonepe.drove.models.events.events.datatags.ClusterEventDataTag;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import java.util.Map;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DroveClusterMaintenanceModeSetEvent extends DroveClusterEvent {
    public DroveClusterMaintenanceModeSetEvent(@JsonProperty("metadata") Map<ClusterEventDataTag, Object> metadata) {
        super(DroveEventType.MAINTENANCE_MODE_SET, metadata);
    }

    @Override
    public <T> T accept(DroveEventVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
