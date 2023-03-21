package com.phonepe.drove.models.events.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.phonepe.drove.models.events.DroveEvent;
import com.phonepe.drove.models.events.DroveEventType;
import com.phonepe.drove.models.events.events.datatags.TaskInstanceEventDataTag;
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
public class DroveTaskStateChangeEvent extends DroveEvent<TaskInstanceEventDataTag> {

    @Builder
    public DroveTaskStateChangeEvent(
            @JsonProperty("metadata") Map<TaskInstanceEventDataTag, Object> metadata) {
        super(DroveEventType.TASK_STATE_CHANGE, metadata);
    }

    @Override
    public <T> T accept(DroveEventVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
