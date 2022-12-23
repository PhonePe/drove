package com.phonepe.drove.controller.event;

import java.util.List;

/**
 *
 */
@SuppressWarnings("rawtypes")
public interface EventStore {

    void recordEvent(final DroveEvent event);

    List<DroveEvent> latest(long lastSyncTime, int size);
}
