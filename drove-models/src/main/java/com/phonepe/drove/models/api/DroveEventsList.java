package com.phonepe.drove.models.api;

import com.phonepe.drove.models.events.DroveEvent;
import lombok.Value;

import java.util.List;

/**
 * Response for events. The last sync time should be sent in the next events call
 */
@Value
public class DroveEventsList {
    @SuppressWarnings("rawtypes")
    List<DroveEvent> events;
    long lastSyncTime;
}
