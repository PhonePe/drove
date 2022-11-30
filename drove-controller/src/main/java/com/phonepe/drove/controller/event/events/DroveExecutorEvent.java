package com.phonepe.drove.controller.event.events;

import com.phonepe.drove.controller.event.DroveEvent;
import com.phonepe.drove.controller.event.DroveEventType;
import com.phonepe.drove.controller.event.events.datatags.ExecutorEventDataTag;

import java.util.Map;

/**
 *
 */
public class DroveExecutorEvent extends DroveEvent<ExecutorEventDataTag> {
    public DroveExecutorEvent(
            DroveEventType type,
            Map<ExecutorEventDataTag, Object> metadata) {
        super(type, metadata);
    }
}
