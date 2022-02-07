package com.phonepe.drove.controller.managed;

import com.phonepe.drove.controller.event.DroveEventBus;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 *
 */
@Slf4j
@Order(50)
@Singleton
public class DroveEventLogger implements Managed {
    private final DroveEventBus eventBus;

    @Inject
    public DroveEventLogger(DroveEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void start() throws Exception {
        eventBus.onNewEvent().connect("event-logger", e -> log.info("DROVE_EVENT: {}", e));
    }

    @Override
    public void stop() throws Exception {
        eventBus.onNewEvent().disconnect("event-logger");
    }
}
