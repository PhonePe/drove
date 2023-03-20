package com.phonepe.drove.models.events.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.phonepe.drove.models.events.DroveEvent;
import com.phonepe.drove.models.events.DroveEventType;
import com.phonepe.drove.models.events.events.datatags.AppEventDataTag;
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
public class DroveAppStateChangeEvent extends DroveEvent<AppEventDataTag> {

    @Builder
    public DroveAppStateChangeEvent(@JsonProperty("metadata") Map<AppEventDataTag, Object> metadata) {
        super(DroveEventType.APP_STATE_CHANGE, metadata);
    }
}
