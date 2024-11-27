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

package com.phonepe.drove.executor.engine;

import com.google.common.collect.Sets;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.common.model.LocalServiceInstanceSpec;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.model.executor.StartLocalServiceInstanceMessage;
import com.phonepe.drove.common.model.executor.StopLocalServiceInstanceMessage;
import com.phonepe.drove.executor.AbstractExecutorEngineEnabledTestBase;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.models.application.PortSpec;
import com.phonepe.drove.models.application.PortType;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.logging.LocalLoggingSpec;
import com.phonepe.drove.models.common.HTTPVerb;
import com.phonepe.drove.models.common.Protocol;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.phonepe.drove.common.CommonTestUtils.waitUntil;
import static com.phonepe.drove.models.instance.LocalServiceInstanceState.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class LocalServiceInstanceEngineTest extends AbstractExecutorEngineEnabledTestBase {

    @Test
    void testBasicRun() {
        val spec = ExecutorTestingUtils.testLocalServiceInstanceSpec();
        val instanceId = spec.getInstanceId();
        val stateChanges = new HashSet<LocalServiceInstanceState>();
        localServiceInstanceEngine.onStateChange().connect(state -> {
            if (state.getInstanceId().equals(instanceId)) {
                stateChanges.add(state.getState());
            }
        });
        val executorAddress = new ExecutorAddress("eid", "localhost", 3000, NodeTransportType.HTTP);
        val startInstanceMessage = new StartLocalServiceInstanceMessage(MessageHeader.controllerRequest(),
                                                                        executorAddress,
                                                                        spec);
        val messageHandler = new ExecutorMessageHandler(applicationInstanceEngine,
                                                        taskInstanceEngine,
                                                        localServiceInstanceEngine,
                                                        executorStateManager);
        val startResponse = startInstanceMessage.accept(messageHandler);
        assertEquals(MessageDeliveryStatus.ACCEPTED, startResponse.getStatus());
        assertEquals(MessageDeliveryStatus.FAILED, startInstanceMessage.accept(messageHandler).getStatus());
        waitUntil(() -> localServiceInstanceEngine.currentState(instanceId)
                .map(LocalServiceInstanceInfo::getState)
                .map(instanceState -> instanceState.equals(HEALTHY))
                .orElse(false));
        assertTrue(localServiceInstanceEngine.exists(instanceId));
        assertFalse(localServiceInstanceEngine.exists("WrongId"));
        val info = localServiceInstanceEngine.currentState(instanceId).orElse(null);
        assertNotNull(info);
        assertEquals(HEALTHY, info.getState());
        val allInfo = localServiceInstanceEngine.currentState();
        assertEquals(1, allInfo.size());
        assertEquals(info.getInstanceId(), allInfo.get(0).getInstanceId());

        val stopInstanceMessage = new StopLocalServiceInstanceMessage(MessageHeader.controllerRequest(),
                                                                      executorAddress,
                                                                      instanceId);
        assertEquals(MessageDeliveryStatus.ACCEPTED, stopInstanceMessage.accept(messageHandler).getStatus());
        waitUntil(() -> localServiceInstanceEngine.currentState(instanceId).isEmpty());
        assertEquals(MessageDeliveryStatus.FAILED, stopInstanceMessage.accept(messageHandler).getStatus());
        val statesDiff = Sets.difference(stateChanges,
                                         EnumSet.of(PENDING,
                                                    PROVISIONING,
                                                    STARTING,
                                                    UNREADY,
                                                    READY,
                                                    HEALTHY,
                                                    DEPROVISIONING,
                                                    STOPPING,
                                                    STOPPED));
        assertTrue(statesDiff.isEmpty());
    }

    @Test
    void testInvalidResourceAllocation() {
        val spec = new LocalServiceInstanceSpec("T001",
                                                "TEST_SPEC",
                                                UUID.randomUUID().toString(),
                                                new DockerCoordinates(
                                                       CommonTestUtils.LOCAL_SERVICE_IMAGE_NAME,
                                                       io.dropwizard.util.Duration.seconds(100)),
                                                List.of(new CPUAllocation(Collections.singletonMap(0,
                                                                                                           Set.of(-1,
                                                                                                                  -3))),
                                                                new MemoryAllocation(Collections.singletonMap(0,
                                                                                                              512L))),
                                                Collections.singletonList(new PortSpec("main", 8000, PortType.HTTP)),
                                                Collections.emptyList(),
                                                Collections.emptyList(),
                                                new CheckSpec(new HTTPCheckModeSpec(Protocol.HTTP,
                                                                                   "main",
                                                                                   "/",
                                                                                   HTTPVerb.GET,
                                                                                   Collections.singleton(200),
                                                                                   "",
                                                                                   io.dropwizard.util.Duration.seconds(1),
                                                                                   false),
                                                             io.dropwizard.util.Duration.seconds(1),
                                                             io.dropwizard.util.Duration.seconds(3),
                                                             3,
                                                             io.dropwizard.util.Duration.seconds(0)),
                                                new CheckSpec(new HTTPCheckModeSpec(Protocol.HTTP,
                                                                                   "main",
                                                                                   "/",
                                                                                   HTTPVerb.GET,
                                                                                   Collections.singleton(200),
                                                                                   "",
                                                                                   io.dropwizard.util.Duration.seconds(1),
                                                                                   false),
                                                             io.dropwizard.util.Duration.seconds(1),
                                                             io.dropwizard.util.Duration.seconds(3),
                                                             3,
                                                             io.dropwizard.util.Duration.seconds(0)),
                                                LocalLoggingSpec.DEFAULT,
                                                Collections.emptyMap(),
                                                null,
                                                Collections.emptyList(),
                                                null,
                                                "TestToken");
        val executorAddress = new ExecutorAddress("eid", "localhost", 3000, NodeTransportType.HTTP);
        val startInstanceMessage = new StartLocalServiceInstanceMessage(MessageHeader.controllerRequest(),
                                                            executorAddress,
                                                            spec);
        val messageHandler = new ExecutorMessageHandler(applicationInstanceEngine,
                                                        taskInstanceEngine,
                                                        localServiceInstanceEngine,
                                                        executorStateManager);
        val startResponse = startInstanceMessage.accept(messageHandler);
        assertEquals(MessageDeliveryStatus.FAILED, startResponse.getStatus());
    }

    @Test
    void testInvalidStop() {
        assertFalse(localServiceInstanceEngine.stopInstance("test"));
    }
}