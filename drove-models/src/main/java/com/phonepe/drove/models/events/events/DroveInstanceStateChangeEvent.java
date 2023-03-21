package com.phonepe.drove.models.events.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.phonepe.drove.models.events.DroveEvent;
import com.phonepe.drove.models.events.DroveEventType;
import com.phonepe.drove.models.events.events.datatags.AppInstanceEventDataTag;
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
public class DroveInstanceStateChangeEvent extends DroveEvent<AppInstanceEventDataTag> {

    @Builder
    public DroveInstanceStateChangeEvent(@JsonProperty("metadata") Map<AppInstanceEventDataTag, Object> metadata) {
        super(DroveEventType.INSTANCE_STATE_CHANGE, metadata);
    }

    @Override
    public <T> T accept(DroveEventVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
