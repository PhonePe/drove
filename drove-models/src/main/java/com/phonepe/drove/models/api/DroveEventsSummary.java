package com.phonepe.drove.models.api;

import com.phonepe.drove.models.events.DroveEventType;
import lombok.Value;

import java.util.Map;

/**
 * Response for events. The last sync time should be sent in the next events call
 */
@Value
public class DroveEventsSummary {
    Map<DroveEventType, Long> eventsCount;
    long lastSyncTime;
}
