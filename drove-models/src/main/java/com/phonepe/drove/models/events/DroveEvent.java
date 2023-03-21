package com.phonepe.drove.models.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.phonepe.drove.models.events.events.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 *
 */
@Data
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "APP_STATE_CHANGE", value = DroveAppStateChangeEvent.class),
        @JsonSubTypes.Type(name = "INSTANCE_STATE_CHANGE", value = DroveInstanceStateChangeEvent.class),
        @JsonSubTypes.Type(name = "TASK_STATE_CHANGE", value = DroveTaskStateChangeEvent.class),
        @JsonSubTypes.Type(names = {"EXECUTOR_ADDED", "EXECUTOR_REMOVED", "EXECUTOR_BLACKLISTED", "EXECUTOR_UN_BLACKLISTED"}, value = DroveExecutorEvent.class),
        @JsonSubTypes.Type(names = {"MAINTENANCE_MODE_SET", "MAINTENANCE_MODE_REMOVED", "LEADERSHIP_ACQUIRED", "LEADERSHIP_LOST"}, value = DroveClusterEvent.class),
})
public abstract class DroveEvent<T extends Enum<T>> {
    private final DroveEventType type;
    private final String id = UUID.randomUUID().toString();
    private final Date time = new Date();
    private Map<T, Object> metadata;

    public static<T> MapBuilder<T, Object> metadataBuilder() {
        return new MapBuilder<>();
    }
}
