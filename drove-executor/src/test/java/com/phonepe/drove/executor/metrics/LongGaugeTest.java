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

import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
class LongGaugeTest {

    @Test
    void testGauge() {
        val ctr = new AtomicInteger();
        val g = new LongGauge<Void>() {
            @Override
            public void consume(Void data) {
                setValue(ctr.longValue());
            }
        };

        assertEquals(0L, g.getValue());
        ctr.incrementAndGet();
        g.consume(null);
        assertEquals(1L, g.getValue());
    }
}