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
class AverageCpuUsageGaugeTest extends AbstractTestBase {
    @SneakyThrows
    @Test
    void testGauge() {
        val g = new AverageCpuUsageGauge();

        g.consume(readJsonResource("/avg-cpu-gauge-test/stats-first.json", Statistics.class));
        assertEquals(0.0, g.getValue());
        g.consume(readJsonResource("/avg-cpu-gauge-test/stats-next.json", Statistics.class));
        System.out.println(g.getValue());
        assertTrue(g.getValue() > 0.0);

    }

    @SneakyThrows
    @ParameterizedTest
    @ValueSource(strings = {
            "/avg-cpu-gauge-test/stats-wrong-cpu-no-data.json",
            "/avg-cpu-gauge-test/stats-wrong-cpu-no-usage.json",
            "/avg-cpu-gauge-test/stats-wrong-cpu-no-total.json",
            "/avg-cpu-gauge-test/stats-wrong-cpu-no-system-cpu.json"
    })
    void testNoData(final String wrongFileName) {
        val g = new AverageCpuUsageGauge();

        g.consume(readJsonResource(wrongFileName, Statistics.class));
        assertEquals(0.0, g.getValue());

    }
}