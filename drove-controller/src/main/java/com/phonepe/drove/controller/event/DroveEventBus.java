package com.phonepe.drove.controller.event;

import io.appform.signals.signals.ConsumingFireForgetSignal;

import javax.inject.Singleton;

/**
 *
 */
@Singleton
public class DroveEventBus {
    private final ConsumingFireForgetSignal<DroveEvent> eventGenerated = new ConsumingFireForgetSignal<>();

    public final void publish(final DroveEvent event) {
        eventGenerated.dispatch(event);
    }

    public final ConsumingFireForgetSignal<DroveEvent> onNewEvent() {
        return eventGenerated;
    }
}
