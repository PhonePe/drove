package com.phonepe.drove.models.events.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.phonepe.drove.models.events.DroveEventType;
import com.phonepe.drove.models.events.events.datatags.ExecutorEventDataTag;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import java.util.Map;

/**
 * An executor is added to the cluster
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DroveExecutorUnblacklistedEvent extends DroveExecutorEvent {
    public DroveExecutorUnblacklistedEvent(@JsonProperty("metadata") Map<ExecutorEventDataTag, Object> metadata) {
        super(DroveEventType.EXECUTOR_UN_BLACKLISTED, metadata);
    }

    @Override
    public <T> T accept(DroveEventVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
