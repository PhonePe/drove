package com.phonepe.drove.controller.event.events;

import com.phonepe.drove.controller.event.DroveEvent;
import com.phonepe.drove.controller.event.DroveEventType;
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
public class DroveStateChangeEvent extends DroveEvent<TaskInstanceEventDataTag> {

    public DroveStateChangeEvent(
            Map<TaskInstanceEventDataTag, Object> metadata) {
        super(DroveEventType.TASK_STATE_CHANGE, metadata);
    }
}
