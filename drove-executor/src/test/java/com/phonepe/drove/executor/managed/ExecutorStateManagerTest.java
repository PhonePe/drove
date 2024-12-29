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

import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.discovery.ClusterClient;
import com.phonepe.drove.executor.discovery.ControllerCommunicationError;
import com.phonepe.drove.executor.engine.LocalServiceInstanceEngine;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.internal.LocalServiceInstanceResources;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test {@link ExecutorStateManager}
 */
class ExecutorStateManagerTest {

    @Test
    @SneakyThrows
    void testStateManagement() {
        val lsie = mock(LocalServiceInstanceEngine.class);
        val clusterClient = mock(ClusterClient.class);
        val exSM = new ExecutorStateManager(lsie, clusterClient);
        val spec = ExecutorTestingUtils.testLocalServiceInstanceSpec();
        try {
            val clusterRetryCtr = new AtomicInteger(0);
            val instanceRetryCtr = new AtomicInteger(0);
            when(clusterClient.reservedResources())
                    .thenAnswer(invocationOnMock -> {
                        if(clusterRetryCtr.getAndIncrement() == 1) {
                            return new LocalServiceInstanceResources(2, 512, Map.of(spec.getServiceId(), 1));
                        }
                        throw new ControllerCommunicationError("Cluster exception");
                    });
            when(lsie.currentState())
                    .thenAnswer(invocation -> switch(instanceRetryCtr.getAndIncrement()) {
                        case 0 -> List.of();
                        case 2 -> List.of(ExecutorUtils.convertToLocalServiceInstance(
                                ExecutorTestingUtils.createExecutorLocaLServiceInstanceInfo(spec, 8000),
                                LocalServiceInstanceState.HEALTHY,
                                ""));
                        default -> throw new IllegalStateException("Test Exception");
                    });
            val currState = new AtomicReference<ExecutorState>();
            exSM.onStateChange().connect(currState::set);
            exSM.start();
            CommonTestUtils.waitUntil(() -> ExecutorState.ACTIVE.equals(currState.get()));
        }
        finally {
            exSM.stop();
        }
    }
}