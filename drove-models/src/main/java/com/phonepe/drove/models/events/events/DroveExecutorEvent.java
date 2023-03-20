package com.phonepe.drove.models.events.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.phonepe.drove.models.events.DroveEvent;
import com.phonepe.drove.models.events.DroveEventType;
import com.phonepe.drove.models.events.events.datatags.ExecutorEventDataTag;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
public class DroveExecutorEvent extends DroveEvent<ExecutorEventDataTag> {
    @Builder
    public DroveExecutorEvent(
            DroveEventType type,
            @JsonProperty("metadata") Map<ExecutorEventDataTag, Object> metadata) {
        super(type, metadata);
    }
}
