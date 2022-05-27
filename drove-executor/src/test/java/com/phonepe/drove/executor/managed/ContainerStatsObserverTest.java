package com.phonepe.drove.executor.managed;

import com.codahale.metrics.MetricFilter;
import com.phonepe.drove.executor.AbstractExecutorEngineEnabledTestBase;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.phonepe.drove.common.CommonTestUtils.waitUntil;
import static com.phonepe.drove.executor.ExecutorTestingUtils.executeOnceContainerStarted;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
@Slf4j
class ContainerStatsObserverTest extends AbstractExecutorEngineEnabledTestBase {

    @Test
    @SneakyThrows
    void testStats() {
        val statsObserver = new ContainerStatsObserver(METRIC_REGISTRY, engine, ExecutorTestingUtils.DOCKER_CLIENT, Duration.ofSeconds(1));
        statsObserver.start();
        val instanceId = executeOnceContainerStarted(engine, info -> {
            log.info("Container is healthy, will look for gauge to be published");
            waitUntil(() -> gaugePresent(info.getInstanceId()));
            assertTrue(gaugePresent(info.getInstanceId()));
            return info.getInstanceId();
        });
        assertNotNull(instanceId);
        waitUntil(() -> !engine.exists(instanceId));
        assertTrue(METRIC_REGISTRY.getGauges(MetricFilter.endsWith("nr_throttled"))
                           .keySet()
                           .stream()
                           .noneMatch(name -> name.contains(instanceId)),
                   "Existing gauges: " + METRIC_REGISTRY.getGauges().keySet());
        statsObserver.stop();
    }

    private boolean gaugePresent(String instanceId) {
        return METRIC_REGISTRY.getGauges(MetricFilter.endsWith("nr_throttled"))
                .keySet()
                .stream()
                .anyMatch(name -> name.contains(instanceId));
    }
}