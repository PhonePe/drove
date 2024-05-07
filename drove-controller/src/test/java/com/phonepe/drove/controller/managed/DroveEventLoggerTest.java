package com.phonepe.drove.controller.managed;

import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.event.EventStore;
import com.phonepe.drove.models.api.DroveEventsList;
import com.phonepe.drove.models.api.DroveEventsSummary;
import com.phonepe.drove.models.events.DroveEvent;
import com.phonepe.drove.models.events.events.DroveClusterMaintenanceModeSetEvent;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
class DroveEventLoggerTest {
    @Test
    @SneakyThrows
    void test() {
        val called = new AtomicBoolean(false);
        val eStore = new EventStore() {
            @Override
            public void recordEvent(DroveEvent event) {
                called.set(true);
            }

            @Override
            public DroveEventsList latest(long lastSyncTime, int size) {
                return null;
            }

            @Override
            public DroveEventsSummary summarize(long lastSyncTime) {
                return null;
            }
        };
        val eventBus = new DroveEventBus();
        val logger = new DroveEventLogger(eventBus, eStore);
        logger.start();
        eventBus.publish(new DroveClusterMaintenanceModeSetEvent(Map.of()));
        CommonTestUtils.waitUntil(called::get);
        logger.stop();
        assertTrue(called.get());
    }
}