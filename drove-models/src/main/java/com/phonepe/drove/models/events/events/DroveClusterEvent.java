package com.phonepe.drove.models.events.events;

import com.phonepe.drove.models.events.DroveEvent;
import com.phonepe.drove.models.events.DroveEventType;
import com.phonepe.drove.models.events.events.datatags.ClusterEventDataTag;

import java.util.Map;

/**
 *
 */
public abstract class DroveClusterEvent extends DroveEvent<ClusterEventDataTag> {

    DroveClusterEvent(DroveEventType type, Map<ClusterEventDataTag, Object> metadata) {
        super(type, metadata);
    }
}
