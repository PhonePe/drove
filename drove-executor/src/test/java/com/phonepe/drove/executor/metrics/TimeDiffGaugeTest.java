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

package com.phonepe.drove.executor.metrics;

import com.phonepe.drove.common.CommonTestUtils;
import io.appform.signals.signals.ScheduledSignal;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
class TimeDiffGaugeTest {

    @Test
    @SneakyThrows
    void testTimeDiffGauge() {
        val ctr = new AtomicInteger();
        val g = new TimeDiffGauge<Void>() {
            @Override
            public void consume(Void data) {}
        };

        try (val incS = new ScheduledSignal(Duration.ofSeconds(1)); val s = new ScheduledSignal(Duration.ofSeconds(5))) {
            val out = new AtomicLong();
            incS.connect(d -> ctr.incrementAndGet());
            s.connect(d -> {
                g.setValue(ctr.longValue() * 10_000);
                out.addAndGet(g.getValue());
            });
            CommonTestUtils.delay(Duration.ofSeconds(15));
            val v = out.get();
            assertTrue(400_000 < v && v < 600_000);
        }
    }

}