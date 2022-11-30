package com.phonepe.drove.controller.event.events;

import com.phonepe.drove.controller.event.DroveEvent;
import com.phonepe.drove.controller.event.DroveEventType;
import com.phonepe.drove.controller.event.events.datatags.ClusterEventDataTag;

import java.util.Map;

/**
 *
 */
public class DroveClusterEvent extends DroveEvent<ClusterEventDataTag> {
    public DroveClusterEvent(
            DroveEventType type,
            Map<ClusterEventDataTag, Object> metadata) {
        super(type, metadata);
    }
}
