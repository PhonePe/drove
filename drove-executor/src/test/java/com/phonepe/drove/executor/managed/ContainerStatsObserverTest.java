/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
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
        val statsObserver = new ContainerStatsObserver(METRIC_REGISTRY,
                                                       applicationInstanceEngine,
                                                       localServiceInstanceEngine,
                                                       ExecutorTestingUtils.DOCKER_CLIENT, Duration.ofSeconds(1));
        statsObserver.start();
        val instanceId = executeOnceContainerStarted(
                applicationInstanceEngine,
                taskInstanceEngine,
                localServiceInstanceEngine,
                info -> {
                    log.info("Container is healthy, will look for gauge to be published");
                    waitUntil(() -> gaugePresent(info.getInstanceId()));
                    assertTrue(gaugePresent(info.getInstanceId()));
                    return info.getInstanceId();
                });
        assertNotNull(instanceId);
        waitUntil(() -> !applicationInstanceEngine.exists(instanceId));
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