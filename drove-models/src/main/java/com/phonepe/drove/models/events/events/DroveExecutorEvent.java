package com.phonepe.drove.models.events.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.phonepe.drove.models.events.DroveEvent;
import com.phonepe.drove.models.events.DroveEventType;
import com.phonepe.drove.models.events.events.datatags.ExecutorEventDataTag;

import java.util.Map;

/**
 *
 */
public abstract class  DroveExecutorEvent extends DroveEvent<ExecutorEventDataTag> {
    protected DroveExecutorEvent(
            DroveEventType type,
            @JsonProperty("metadata") Map<ExecutorEventDataTag, Object> metadata) {
        super(type, metadata);
    }
}
