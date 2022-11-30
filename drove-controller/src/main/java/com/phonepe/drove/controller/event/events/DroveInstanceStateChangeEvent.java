package com.phonepe.drove.controller.event.events;

import com.phonepe.drove.controller.event.DroveEvent;
import com.phonepe.drove.controller.event.DroveEventType;
import com.phonepe.drove.controller.event.events.datatags.AppInstanceEventDataTag;
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
public class DroveInstanceStateChangeEvent extends DroveEvent<AppInstanceEventDataTag> {

    public DroveInstanceStateChangeEvent(Map<AppInstanceEventDataTag, Object> metadata) {
        super(DroveEventType.INSTANCE_STATE_CHANGE, metadata);
    }
}
