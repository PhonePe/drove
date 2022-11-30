package com.phonepe.drove.controller.event.events;

import com.phonepe.drove.controller.event.DroveEvent;
import com.phonepe.drove.controller.event.DroveEventType;
import com.phonepe.drove.controller.event.events.datatags.AppEventDataTag;
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
public class DroveAppStateChangeEvent extends DroveEvent<AppEventDataTag> {

    public DroveAppStateChangeEvent(Map<AppEventDataTag, Object> metadata) {
        super(DroveEventType.APP_STATE_CHANGE, metadata);
    }
}
