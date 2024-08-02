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

import com.github.dockerjava.api.model.Statistics;
import com.phonepe.drove.common.AbstractTestBase;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
class PerCoreCpuUsageGaugeTest extends AbstractTestBase {

    @Test
    void test() {
        val g = new PerCoreCpuUsageGauge(4);
        g.consume(readJsonResource("/per-core-usage-test/stats-first.json", Statistics.class));
        assertEquals(0.0, g.getValue());
        g.consume(readJsonResource("/per-core-usage-test/stats-next.json", Statistics.class));
        assertTrue(37 > g.getValue() && g.getValue() > 36); //Avoiding equality comparing doubles
    }

    @SneakyThrows
    @ParameterizedTest
    @ValueSource(strings = {
            "/per-core-usage-test/stats-wrong-cpu-no-stats.json",
            "/per-core-usage-test/stats-wrong-cpu-no-usage.json",
            "/per-core-usage-test/stats-wrong-cpu-no-total.json",
    })
    void testNoData(final String wrongFileName) {
        val g = new PerCoreCpuUsageGauge(4);

        g.consume(readJsonResource(wrongFileName, Statistics.class));
        assertEquals(0.0, g.getValue());
    }

    @Test
    void testNopCoreData() {
        val g = new PerCoreCpuUsageGauge(4);
        g.consume(readJsonResource("/per-core-usage-test/stats-first.json", Statistics.class));
        assertEquals(0.0, g.getValue());
        g.consume(readJsonResource("/per-core-usage-test/stats-wrong-cpu-no-core-data.json", Statistics.class));
        assertEquals(0.0, g.getValue());
    }
}