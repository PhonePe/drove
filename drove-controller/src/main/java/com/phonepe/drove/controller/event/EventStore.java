package com.phonepe.drove.controller.event;

import com.phonepe.drove.models.events.DroveEvent;

import java.util.List;

/**
 *
 */
@SuppressWarnings("rawtypes")
public interface EventStore {

    void recordEvent(final DroveEvent event);

    List<DroveEvent> latest(long lastSyncTime, int size);
}
