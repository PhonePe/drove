package com.phonepe.drove.controller.event;

import com.phonepe.drove.models.events.DroveEvent;
import io.appform.functionmetrics.MonitoredFunction;
import io.appform.signals.signals.ConsumingFireForgetSignal;

import javax.inject.Singleton;

/**
 *
 */
@Singleton
public class DroveEventBus {
    private final ConsumingFireForgetSignal<DroveEvent> eventGenerated = new ConsumingFireForgetSignal<>();

    @MonitoredFunction
    public final void publish(final DroveEvent event) {
        eventGenerated.dispatch(event);
    }

    public final ConsumingFireForgetSignal<DroveEvent> onNewEvent() {
        return eventGenerated;
    }
}
