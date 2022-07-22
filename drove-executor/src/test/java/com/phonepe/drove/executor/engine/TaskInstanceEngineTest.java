package com.phonepe.drove.executor.engine;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.model.executor.StartTaskInstanceMessage;
import com.phonepe.drove.common.model.executor.StopTaskInstanceMessage;
import com.phonepe.drove.executor.AbstractExecutorEngineEnabledTestBase;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.logging.LocalLoggingSpec;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.taskinstance.TaskInstanceInfo;
import com.phonepe.drove.models.taskinstance.TaskInstanceState;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

import static com.phonepe.drove.common.CommonTestUtils.delay;
import static com.phonepe.drove.common.CommonTestUtils.waitUntil;
import static com.phonepe.drove.models.taskinstance.TaskInstanceState.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class TaskInstanceEngineTest extends AbstractExecutorEngineEnabledTestBase {
    @Test
    void testBasicRun() {
        val spec = ExecutorTestingUtils.testTaskInstanceSpec();
        val instanceId = CommonUtils.instanceId(spec);
        val stateChanges = new HashSet<TaskInstanceState>();
        taskInstanceEngine.onStateChange().connect(state -> {
            if (state.getTaskId().equals(instanceId)) {
                stateChanges.add(state.getState());
            }
        });
        val executorAddress = new ExecutorAddress("eid", "localhost", 3000, NodeTransportType.HTTP);
        val startInstanceMessage = new StartTaskInstanceMessage(MessageHeader.controllerRequest(),
                                                                executorAddress,
                                                                spec);
        val messageHandler = new ExecutorMessageHandler(applicationInstanceEngine,
                                                        taskInstanceEngine,
                                                        blacklistingManager);
        val startResponse = startInstanceMessage.accept(messageHandler);
        assertEquals(MessageDeliveryStatus.ACCEPTED, startResponse.getStatus());
        assertEquals(MessageDeliveryStatus.FAILED, startInstanceMessage.accept(messageHandler).getStatus());
        waitUntil(() -> taskInstanceEngine.currentState(instanceId)
                .map(TaskInstanceInfo::getState)
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
                                               .map(TaskInstanceInfo::getState)
                                               .orElse(STOPPED)));
        /*val stopInstanceMessage = new StopTaskInstanceMessage(MessageHeader.controllerRequest(),
                                                              executorAddress,
                                                              instanceId);
        assertEquals(MessageDeliveryStatus.ACCEPTED, stopInstanceMessage.accept(messageHandler).getStatus());
        waitUntil(() -> taskInstanceEngine.currentState(instanceId).isEmpty());
        assertEquals(MessageDeliveryStatus.FAILED, stopInstanceMessage.accept(messageHandler).getStatus());*/
        val statesDiff = Sets.difference(stateChanges,
                                         EnumSet.of(PENDING,
                                                    PROVISIONING,
                                                    STARTING,
                                                    RUNNING,
                                                    RUN_COMPLETED,
                                                    DEPROVISIONING,
                                                    STOPPING,
                                                    STOPPED));
        assertTrue(statesDiff.isEmpty(), "Diff: " + statesDiff);
    }

    @Test
    void testBasicRunTestFailed() {
        val spec = ExecutorTestingUtils.testTaskInstanceSpec(Map.of("ITERATIONS", "2", "EXIT_CODE", "-1"));
        val instanceId = CommonUtils.instanceId(spec);
        val stateChanges = new HashSet<TaskInstanceState>();
        taskInstanceEngine.onStateChange().connect(state -> {
            if (state.getTaskId().equals(instanceId)) {
                stateChanges.add(state.getState());
            }
        });
        val executorAddress = new ExecutorAddress("eid", "localhost", 3000, NodeTransportType.HTTP);
        val startInstanceMessage = new StartTaskInstanceMessage(MessageHeader.controllerRequest(),
                                                                executorAddress,
                                                                spec);
        val messageHandler = new ExecutorMessageHandler(applicationInstanceEngine,
                                                        taskInstanceEngine,
                                                        blacklistingManager);
        val startResponse = startInstanceMessage.accept(messageHandler);
        assertEquals(MessageDeliveryStatus.ACCEPTED, startResponse.getStatus());
        assertEquals(MessageDeliveryStatus.FAILED, startInstanceMessage.accept(messageHandler).getStatus());
        waitUntil(() -> taskInstanceEngine.currentState(instanceId)
                .map(TaskInstanceInfo::getState)
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
                                               .map(TaskInstanceInfo::getState)
                                               .orElse(STOPPED)));
        /*val stopInstanceMessage = new StopTaskInstanceMessage(MessageHeader.controllerRequest(),
                                                              executorAddress,
                                                              instanceId);
        assertEquals(MessageDeliveryStatus.ACCEPTED, stopInstanceMessage.accept(messageHandler).getStatus());
        waitUntil(() -> taskInstanceEngine.currentState(instanceId).isEmpty());
        assertEquals(MessageDeliveryStatus.FAILED, stopInstanceMessage.accept(messageHandler).getStatus());*/
        val statesDiff = Sets.difference(stateChanges,
                                         EnumSet.of(PENDING,
                                                    PROVISIONING,
                                                    STARTING,
                                                    RUNNING,
                                                    RUN_FAILED,
                                                    DEPROVISIONING,
                                                    STOPPING,
                                                    STOPPED));
        assertTrue(statesDiff.isEmpty(), "Diff: " + statesDiff);
    }

    @Test
    void testBasicRunTestCancel() {
        val spec = ExecutorTestingUtils.testTaskInstanceSpec(Map.of("ITERATIONS", "200"));
        val instanceId = spec.getTaskId();
        val stateChanges = new HashSet<TaskInstanceState>();
        taskInstanceEngine.onStateChange().connect(state -> {
            if (state.getTaskId().equals(instanceId)) {
                stateChanges.add(state.getState());
            }
        });
        val executorAddress = new ExecutorAddress("eid", "localhost", 3000, NodeTransportType.HTTP);
        val startInstanceMessage = new StartTaskInstanceMessage(MessageHeader.controllerRequest(),
                                                                executorAddress,
                                                                spec);
        val messageHandler = new ExecutorMessageHandler(applicationInstanceEngine,
                                                        taskInstanceEngine,
                                                        blacklistingManager);
        val startResponse = startInstanceMessage.accept(messageHandler);
        assertEquals(MessageDeliveryStatus.ACCEPTED, startResponse.getStatus());
        assertEquals(MessageDeliveryStatus.FAILED, startInstanceMessage.accept(messageHandler).getStatus());
        waitUntil(() -> taskInstanceEngine.currentState(instanceId)
                .map(TaskInstanceInfo::getState)
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
                                               .map(TaskInstanceInfo::getState)
                                               .orElse(STOPPED)));
        delay(Duration.ofSeconds(5));
        val stopInstanceMessage = new StopTaskInstanceMessage(MessageHeader.controllerRequest(),
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
                                                    RUN_CANCELLED,
                                                    DEPROVISIONING,
                                                    STOPPING,
                                                    STOPPED));
        assertTrue(statesDiff.isEmpty(), "Diff: " + statesDiff);
    }

    @Test
    void testInvalidResourceAllocation() {
        val spec = new TaskInstanceSpec("T001",
                                        "TEST_SPEC",
                                        new DockerCoordinates(
                                                CommonTestUtils.TASK_IMAGE_NAME,
                                                       io.dropwizard.util.Duration.seconds(100)),
                                        ImmutableList.of(new CPUAllocation(Collections.singletonMap(0, Set.of(-1, -3))),
                                                                new MemoryAllocation(Collections.singletonMap(0, 512L))),
                                        Collections.emptyList(),
                                        LocalLoggingSpec.DEFAULT,
                                        Collections.emptyMap());
        val executorAddress = new ExecutorAddress("eid", "localhost", 3000, NodeTransportType.HTTP);
        val startInstanceMessage = new StartTaskInstanceMessage(MessageHeader.controllerRequest(),
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