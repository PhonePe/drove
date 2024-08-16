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

import com.github.dockerjava.api.model.Container;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.model.executor.StartTaskMessage;
import com.phonepe.drove.common.model.executor.StopTaskMessage;
import com.phonepe.drove.executor.AbstractExecutorEngineEnabledTestBase;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.logging.LocalLoggingSpec;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskResult;
import com.phonepe.drove.models.taskinstance.TaskState;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.phonepe.drove.common.CommonTestUtils.delay;
import static com.phonepe.drove.common.CommonTestUtils.waitUntil;
import static com.phonepe.drove.executor.ExecutorTestingUtils.DOCKER_CLIENT;
import static com.phonepe.drove.models.taskinstance.TaskState.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class TaskInstanceEngineTest extends AbstractExecutorEngineEnabledTestBase {
    @Test
    void testBasicRun() {
        val spec = ExecutorTestingUtils.testTaskInstanceSpec();
        val instanceId = CommonUtils.instanceId(spec);
        val stateChanges = new HashSet<TaskState>();
        val res = new AtomicReference<TaskResult>();
        taskInstanceEngine.onStateChange().connect(state -> {
            if (state.getInstanceId().equals(instanceId)) {
                stateChanges.add(state.getState());
                if(state.getState().isTerminal()) {
                    res.set(state.getTaskResult());
                }
            }
        });
        val executorAddress = new ExecutorAddress("eid", "localhost", 3000, NodeTransportType.HTTP);
        val startInstanceMessage = new StartTaskMessage(MessageHeader.controllerRequest(),
                                                        executorAddress,
                                                        spec);
        val messageHandler = new ExecutorMessageHandler(applicationInstanceEngine,
                                                        taskInstanceEngine,
                                                        blacklistingManager);
        val startResponse = startInstanceMessage.accept(messageHandler);
        assertEquals(MessageDeliveryStatus.ACCEPTED, startResponse.getStatus());
        assertEquals(MessageDeliveryStatus.FAILED, startInstanceMessage.accept(messageHandler).getStatus());
        waitUntil(() -> taskInstanceEngine.currentState(instanceId)
                .map(TaskInfo::getState)
                .map(instanceState -> instanceState.equals(RUNNING))
                .orElse(false));
        assertTrue(taskInstanceEngine.exists(instanceId));
        assertFalse(taskInstanceEngine.exists("WrongId"));
        val info = taskInstanceEngine.currentState(instanceId).orElse(null);
        assertNotNull(info);
        assertEquals(RUNNING, info.getState());
        val allInfo = taskInstanceEngine.currentState();
        assertEquals(1, allInfo.size());
        assertEquals(info.getTaskId(), allInfo.get(0).getTaskId());
        waitUntil(() -> STOPPED.equals(taskInstanceEngine.currentState(instanceId)
                                               .map(TaskInfo::getState)
                                               .orElse(STOPPED)));
        val statesDiff = Sets.difference(stateChanges,
                                         EnumSet.of(PENDING,
                                                    PROVISIONING,
                                                    STARTING,
                                                    RUNNING,
                                                    RUN_COMPLETED,
                                                    DEPROVISIONING,
                                                    STOPPED));
        assertTrue(statesDiff.isEmpty(), "Diff: " + statesDiff);
        assertEquals(new TaskResult(TaskResult.Status.SUCCESSFUL, 0L), res.get());
    }

    @Test
    void testContainerLoss() {
        val spec = ExecutorTestingUtils.testTaskInstanceSpec( Map.of("ITERATIONS", "1000"));
        val instanceId = CommonUtils.instanceId(spec);
        val stateChanges = new HashSet<TaskState>();
        val res = new AtomicReference<TaskResult>();
        taskInstanceEngine.onStateChange().connect(state -> {
            if (state.getInstanceId().equals(instanceId)) {
                stateChanges.add(state.getState());
                if(state.getState().isTerminal()) {
                    res.set(state.getTaskResult());
                }
            }
        });
        val executorAddress = new ExecutorAddress("eid", "localhost", 3000, NodeTransportType.HTTP);
        val startInstanceMessage = new StartTaskMessage(MessageHeader.controllerRequest(),
                                                        executorAddress,
                                                        spec);
        val messageHandler = new ExecutorMessageHandler(applicationInstanceEngine,
                                                        taskInstanceEngine,
                                                        blacklistingManager);
        val startResponse = startInstanceMessage.accept(messageHandler);
        assertEquals(MessageDeliveryStatus.ACCEPTED, startResponse.getStatus());
        assertEquals(MessageDeliveryStatus.FAILED, startInstanceMessage.accept(messageHandler).getStatus());
        waitUntil(() -> taskInstanceEngine.currentState(instanceId)
                .map(TaskInfo::getState)
                .map(instanceState -> instanceState.equals(RUNNING))
                .orElse(false));
        assertTrue(taskInstanceEngine.exists(instanceId));
        assertFalse(taskInstanceEngine.exists("WrongId"));
        val containerId = DOCKER_CLIENT.listContainersCmd()
                .withLabelFilter(Map.of(DockerLabels.DROVE_INSTANCE_ID_LABEL, instanceId))
                .exec()
                .stream()
                .findFirst()
                .map(Container::getId)
                .orElse(null);
        assertNotNull(containerId);
        DOCKER_CLIENT.stopContainerCmd(containerId).exec();
        val info = taskInstanceEngine.currentState(instanceId).orElse(null);
        assertNotNull(info);
        assertEquals(RUNNING, info.getState());
        val allInfo = taskInstanceEngine.currentState();
        assertEquals(1, allInfo.size());
        assertEquals(info.getTaskId(), allInfo.get(0).getTaskId());
        waitUntil(() -> STOPPED.equals(taskInstanceEngine.currentState(instanceId)
                                               .map(TaskInfo::getState)
                                               .orElse(STOPPED)));
        val statesDiff = Sets.difference(stateChanges,
                                         EnumSet.of(PENDING,
                                                    PROVISIONING,
                                                    STARTING,
                                                    RUNNING,
                                                    RUN_COMPLETED,
                                                    DEPROVISIONING,
                                                    STOPPED));
        assertTrue(statesDiff.isEmpty(), "Diff: " + statesDiff);
        assertEquals(new TaskResult(TaskResult.Status.FAILED, 137L), res.get());
    }

    @Test
    void testBasicRunTestFailed() {
        val spec = ExecutorTestingUtils.testTaskInstanceSpec(Map.of("ITERATIONS", "2", "EXIT_CODE", "-1"));
        val instanceId = CommonUtils.instanceId(spec);
        val stateChanges = new HashSet<TaskState>();
        val res = new AtomicReference<TaskResult>();
        taskInstanceEngine.onStateChange().connect(state -> {
            if (state.getInstanceId().equals(instanceId)) {
                stateChanges.add(state.getState());
                if(state.getState().isTerminal()) {
                    res.set(state.getTaskResult());
                }
            }
        });
        val executorAddress = new ExecutorAddress("eid", "localhost", 3000, NodeTransportType.HTTP);
        val startInstanceMessage = new StartTaskMessage(MessageHeader.controllerRequest(),
                                                        executorAddress,
                                                        spec);
        val messageHandler = new ExecutorMessageHandler(applicationInstanceEngine,
                                                        taskInstanceEngine,
                                                        blacklistingManager);
        val startResponse = startInstanceMessage.accept(messageHandler);
        assertEquals(MessageDeliveryStatus.ACCEPTED, startResponse.getStatus());
        assertEquals(MessageDeliveryStatus.FAILED, startInstanceMessage.accept(messageHandler).getStatus());
        waitUntil(() -> taskInstanceEngine.currentState(instanceId)
                .map(TaskInfo::getState)
                .map(instanceState -> instanceState.equals(RUNNING))
                .orElse(false));
        assertTrue(taskInstanceEngine.exists(instanceId));
        assertFalse(taskInstanceEngine.exists("WrongId"));
        val info = taskInstanceEngine.currentState(instanceId).orElse(null);
        assertNotNull(info);
        assertEquals(RUNNING, info.getState());
        val allInfo = taskInstanceEngine.currentState();
        assertEquals(1, allInfo.size());
        assertEquals(info.getTaskId(), allInfo.get(0).getTaskId());
        waitUntil(() -> STOPPED.equals(taskInstanceEngine.currentState(instanceId)
                                               .map(TaskInfo::getState)
                                               .orElse(STOPPED)));

        val statesDiff = Sets.difference(stateChanges,
                                         EnumSet.of(PENDING,
                                                    PROVISIONING,
                                                    STARTING,
                                                    RUNNING,
                                                    RUN_COMPLETED,
                                                    DEPROVISIONING,
                                                    STOPPED));
        assertTrue(statesDiff.isEmpty(), "Diff: " + statesDiff);
        assertEquals(new TaskResult(TaskResult.Status.FAILED, 255L), res.get());
    }

    @Test
    void testBasicRunTestCancel() {
        val spec = ExecutorTestingUtils.testTaskInstanceSpec(Map.of("ITERATIONS", "200"));
        val instanceId = spec.getInstanceId();
        val stateChanges = new HashSet<TaskState>();
        val res = new AtomicReference<TaskResult>();
        taskInstanceEngine.onStateChange().connect(state -> {
            if (state.getInstanceId().equals(instanceId)) {
                stateChanges.add(state.getState());
                if(state.getState().isTerminal()) {
                    res.set(state.getTaskResult());
                }
            }
        });
        val executorAddress = new ExecutorAddress("eid", "localhost", 3000, NodeTransportType.HTTP);
        val startInstanceMessage = new StartTaskMessage(MessageHeader.controllerRequest(),
                                                        executorAddress,
                                                        spec);
        val messageHandler = new ExecutorMessageHandler(applicationInstanceEngine,
                                                        taskInstanceEngine,
                                                        blacklistingManager);
        val startResponse = startInstanceMessage.accept(messageHandler);
        assertEquals(MessageDeliveryStatus.ACCEPTED, startResponse.getStatus());
        assertEquals(MessageDeliveryStatus.FAILED, startInstanceMessage.accept(messageHandler).getStatus());
        waitUntil(() -> taskInstanceEngine.currentState(instanceId)
                .map(TaskInfo::getState)
                .map(instanceState -> instanceState.equals(RUNNING))
                .orElse(false));
        assertTrue(taskInstanceEngine.exists(instanceId));
        assertFalse(taskInstanceEngine.exists("WrongId"));
        val info = taskInstanceEngine.currentState(instanceId).orElse(null);
        assertNotNull(info);
        assertEquals(RUNNING, info.getState());
        val allInfo = taskInstanceEngine.currentState();
        assertEquals(1, allInfo.size());
        assertEquals(info.getTaskId(), allInfo.get(0).getTaskId());
        waitUntil(() -> RUNNING.equals(taskInstanceEngine.currentState(instanceId)
                                               .map(TaskInfo::getState)
                                               .orElse(STOPPED)));
        delay(Duration.ofSeconds(5));
        val stopInstanceMessage = new StopTaskMessage(MessageHeader.controllerRequest(),
                                                      executorAddress,
                                                      instanceId);
        assertEquals(MessageDeliveryStatus.ACCEPTED, stopInstanceMessage.accept(messageHandler).getStatus());
        waitUntil(() -> taskInstanceEngine.currentState(instanceId).isEmpty());
        assertEquals(MessageDeliveryStatus.FAILED, stopInstanceMessage.accept(messageHandler).getStatus());
        val statesDiff = Sets.difference(stateChanges,
                                         EnumSet.of(PENDING,
                                                    PROVISIONING,
                                                    STARTING,
                                                    RUNNING,
                                                    RUN_COMPLETED,
                                                    DEPROVISIONING,
                                                    STOPPED));
        assertTrue(statesDiff.isEmpty(), "Diff: " + statesDiff);
        assertEquals(new TaskResult(TaskResult.Status.CANCELLED, -1L), res.get());
    }

    @Test
    void testInvalidResourceAllocation() {
        val spec = new TaskInstanceSpec("T001",
                                        "TEST_SPEC",
                                        UUID.randomUUID().toString(),
                                        new DockerCoordinates(
                                                CommonTestUtils.TASK_IMAGE_NAME,
                                                       io.dropwizard.util.Duration.seconds(100)),
                                        ImmutableList.of(new CPUAllocation(Collections.singletonMap(0, Set.of(-1, -3))),
                                                                new MemoryAllocation(Collections.singletonMap(0, 512L))),
                                        Collections.emptyList(),
                                        Collections.emptyList(),
                                        LocalLoggingSpec.DEFAULT,
                                        Collections.emptyMap(),
                                        null);
        val executorAddress = new ExecutorAddress("eid", "localhost", 3000, NodeTransportType.HTTP);
        val startInstanceMessage = new StartTaskMessage(MessageHeader.controllerRequest(),
                                                        executorAddress,
                                                        spec);
        val messageHandler = new ExecutorMessageHandler(applicationInstanceEngine, taskInstanceEngine, blacklistingManager);
        val startResponse = startInstanceMessage.accept(messageHandler);
        assertEquals(MessageDeliveryStatus.FAILED, startResponse.getStatus());
    }

    @Test
    void testInvalidStop() {
        assertFalse(applicationInstanceEngine.stopInstance("test"));
    }
}