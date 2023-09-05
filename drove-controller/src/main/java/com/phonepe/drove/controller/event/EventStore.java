package com.phonepe.drove.controller.event;

import com.phonepe.drove.models.api.DroveEventsList;
import com.phonepe.drove.models.api.DroveEventsSummary;
import com.phonepe.drove.models.events.DroveEvent;

/**
 *
 */
@SuppressWarnings("rawtypes")
public interface EventStore {

    void recordEvent(final DroveEvent event);

    DroveEventsList latest(long lastSyncTime, int size);

    DroveEventsSummary summarize(long lastSyncTime);
}
