package com.phonepe.drove.controller.event.events;

import com.phonepe.drove.controller.event.DroveEvent;
import com.phonepe.drove.controller.event.DroveEventType;
import com.phonepe.drove.models.application.ApplicationState;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DroveAppStateChangeEvent extends DroveEvent {
    String appId;
    ApplicationState state;
    public DroveAppStateChangeEvent(
            String appId,
            ApplicationState state) {
        super(DroveEventType.APP_STATE_CHANGE);
        this.appId = appId;
        this.state = state;
    }
}
