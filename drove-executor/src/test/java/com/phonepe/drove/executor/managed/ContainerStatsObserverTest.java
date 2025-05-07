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
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.model.executor.StartInstanceMessage;
import com.phonepe.drove.common.model.executor.StopInstanceMessage;
import com.phonepe.drove.executor.AbstractExecutorEngineEnabledTestBase;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.engine.ExecutorMessageHandler;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.instance.InstanceInfo;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.phonepe.drove.common.CommonTestUtils.waitUntil;
import static com.phonepe.drove.models.instance.InstanceState.HEALTHY;
import static com.phonepe.drove.models.instance.InstanceState.UNKNOWN;
import static org.junit.jupiter.api.Assertions.*;

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
        val spec = ExecutorTestingUtils.testAppInstanceSpec();
        val instanceId = spec.getInstanceId();
        val executorAddress = new ExecutorAddress("eid", "localhost", 3000, NodeTransportType.HTTP);
        val startInstanceMessage = new StartInstanceMessage(MessageHeader.controllerRequest(),
                                                            executorAddress,
                                                            spec);
        val messageHandler = new ExecutorMessageHandler(applicationInstanceEngine, taskInstanceEngine, localServiceInstanceEngine, null);
        val startResponse = startInstanceMessage.accept(messageHandler);
        try {
            assertEquals(MessageDeliveryStatus.ACCEPTED, startResponse.getStatus());
            assertEquals(MessageDeliveryStatus.FAILED, startInstanceMessage.accept(messageHandler).getStatus());
            waitUntil(() -> applicationInstanceEngine.currentState(instanceId)
                    .map(InstanceInfo::getState)
                    .map(instanceState -> instanceState.equals(HEALTHY))
                    .orElse(false));
            val info = applicationInstanceEngine.currentState(instanceId).orElse(null);
            assertNotNull(info);
            assertNotNull(instanceId);
            waitUntil(() -> gaugePresent(instanceId));
            log.info("Found required gauge. Will stop container now and ensure cleanup is done properly");
        }
        finally {
            val stopInstanceMessage = new StopInstanceMessage(MessageHeader.controllerRequest(),
                                                              executorAddress,
                                                              instanceId);
            assertEquals(MessageDeliveryStatus.ACCEPTED, stopInstanceMessage.accept(messageHandler).getStatus());
            waitUntil(() -> !applicationInstanceEngine.currentState(instanceId).map(InstanceInfo::getState).orElse(UNKNOWN).equals(HEALTHY));
        }

        waitUntil(() -> METRIC_REGISTRY.getMetrics()
                .keySet()
                .stream()
                .noneMatch(name -> name.contains(instanceId)));
        val metricsForInstance = METRIC_REGISTRY.getMetrics()
                .keySet()
                .stream()
                .filter(name -> name.contains(instanceId))
                .toList();
        assertTrue(metricsForInstance.isEmpty(),
                    "Container stopped but metric still exists for %s: %s".formatted(instanceId, metricsForInstance));
        statsObserver.stop();
    }

    private boolean gaugePresent(String instanceId) {
        return METRIC_REGISTRY.getGauges(MetricFilter.endsWith("nr_throttled"))
                .keySet()
                .stream()
                .anyMatch(name -> name.contains(instanceId));
    }
}