package com.phonepe.drove.controller.event;

import java.util.List;

/**
 *
 */
@SuppressWarnings("rawtypes")
public interface EventStore {
    int DEFAULT_CAPACITY = 100;

    void recordEvent(final DroveEvent event);

    List<DroveEvent> latest(long lastSyncTime, int size);
}
