/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

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