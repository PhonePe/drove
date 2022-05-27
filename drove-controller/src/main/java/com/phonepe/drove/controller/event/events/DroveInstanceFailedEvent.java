package com.phonepe.drove.controller.event.events;

import com.phonepe.drove.controller.event.DroveEvent;
import com.phonepe.drove.controller.event.DroveEventType;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DroveInstanceFailedEvent extends DroveEvent {
    String appId;
    String instanceId;
    InstanceState state;
    String error;

    public DroveInstanceFailedEvent(
            String appId,
            String instanceId,
            InstanceState state, String error) {
        super(DroveEventType.INSTANCE_FAILED);
        this.appId = appId;
        this.instanceId = instanceId;
        this.state = state;
        this.error = error;
    }
}
