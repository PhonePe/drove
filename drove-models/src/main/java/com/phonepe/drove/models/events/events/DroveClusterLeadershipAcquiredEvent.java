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
public class DroveClusterLeadershipAcquiredEvent extends DroveClusterEvent {
    public DroveClusterLeadershipAcquiredEvent(@JsonProperty("metadata") Map<ClusterEventDataTag, Object> metadata) {
        super(DroveEventType.LEADERSHIP_ACQUIRED, metadata);
    }

    @Override
    public <T> T accept(DroveEventVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
