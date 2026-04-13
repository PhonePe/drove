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

import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.common.model.LocalServiceInstanceSpec;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.common.model.executor.BlacklistExecutorFinalizeMessage;
import com.phonepe.drove.common.model.executor.BlacklistExecutorMessage;
import com.phonepe.drove.common.model.executor.ExecutorMessage;
import com.phonepe.drove.common.model.executor.StartInstanceMessage;
import com.phonepe.drove.common.model.executor.StartLocalServiceInstanceMessage;
import com.phonepe.drove.common.model.executor.StartTaskMessage;
import com.phonepe.drove.common.model.executor.StopInstanceMessage;
import com.phonepe.drove.common.model.executor.StopLocalServiceInstanceMessage;
import com.phonepe.drove.common.model.executor.StopTaskMessage;
import com.phonepe.drove.common.model.executor.UnBlacklistExecutorMessage;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.managed.ExecutorStateManager;
import com.phonepe.drove.models.info.nodedata.ExecutorState;

import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static com.phonepe.drove.common.CommonTestUtils.executor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.stream.Stream;

/**
 *
 */
class ExecutorMessageHandlerTest {

    @ParameterizedTest
    @MethodSource("startMessageProvider")
    void testCreateInstanceMessage(final ExecutorMessage message) {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val localserviceInstanceEngine = mock(LocalServiceInstanceEngine.class);
        val executorStateManager = mock(ExecutorStateManager.class);
        when(executorStateManager.currentState()).thenReturn(ExecutorState.ACTIVE);
        val mh = new ExecutorMessageHandler(applicationEngine,
                                            taskEngine,
                                            localserviceInstanceEngine,
                                            executorStateManager);
        when(applicationEngine.exists(anyString())).thenReturn(false);
        when(applicationEngine.startInstance(any(ApplicationInstanceSpec.class))).thenReturn(true);
        when(taskEngine.exists(anyString())).thenReturn(false);
        when(taskEngine.startInstance(any(TaskInstanceSpec.class))).thenReturn(true);
        when(localserviceInstanceEngine.exists(anyString())).thenReturn(false);
        when(localserviceInstanceEngine.startInstance(any(LocalServiceInstanceSpec.class))).thenReturn(true);
        assertEquals(MessageDeliveryStatus.ACCEPTED, message.accept(mh).getStatus());
    }

    @ParameterizedTest
    @MethodSource("startMessageProvider")
    void testCreateInstanceMessageStateFail(final ExecutorMessage message) {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val localserviceInstanceEngine = mock(LocalServiceInstanceEngine.class);
        val executorStateManager = mock(ExecutorStateManager.class);
        when(executorStateManager.currentState()).thenReturn(ExecutorState.BLACKLISTED);
        val mh = new ExecutorMessageHandler(applicationEngine,
                                            taskEngine,
                                            localserviceInstanceEngine,
                                            executorStateManager);
        when(applicationEngine.exists(anyString())).thenReturn(false);
        when(applicationEngine.startInstance(any(ApplicationInstanceSpec.class))).thenReturn(true);
        when(taskEngine.exists(anyString())).thenReturn(false);
        when(taskEngine.startInstance(any(TaskInstanceSpec.class))).thenReturn(true);
        when(localserviceInstanceEngine.exists(anyString())).thenReturn(false);
        when(localserviceInstanceEngine.startInstance(any(LocalServiceInstanceSpec.class))).thenReturn(true);
        assertEquals(MessageDeliveryStatus.FAILED, message.accept(mh).getStatus());
    }

    @ParameterizedTest
    @MethodSource("startMessageProvider")
    void testCreateInstanceMessageExists(final ExecutorMessage message) {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val localserviceInstanceEngine = mock(LocalServiceInstanceEngine.class);
        val executorStateManager = mock(ExecutorStateManager.class);
        when(executorStateManager.currentState()).thenReturn(ExecutorState.ACTIVE);
        when(applicationEngine.exists(anyString())).thenReturn(true);
        when(taskEngine.exists(anyString())).thenReturn(true);
        when(localserviceInstanceEngine.exists(anyString())).thenReturn(true);
        val mh = new ExecutorMessageHandler(applicationEngine,
                                            taskEngine,
                                            localserviceInstanceEngine,
                                            executorStateManager);

        assertEquals(MessageDeliveryStatus.FAILED, message.accept(mh).getStatus());
    }

    @ParameterizedTest
    @MethodSource("startMessageProvider")
    void testCreateInstanceMessageFail(final ExecutorMessage message) {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val localserviceInstanceEngine = mock(LocalServiceInstanceEngine.class);
        val executorStateManager = mock(ExecutorStateManager.class);
        when(executorStateManager.currentState()).thenReturn(ExecutorState.ACTIVE);
        when(applicationEngine.exists(anyString())).thenReturn(false);
        when(applicationEngine.startInstance(any(ApplicationInstanceSpec.class))).thenReturn(false);
        when(taskEngine.exists(anyString())).thenReturn(false);
        when(taskEngine.startInstance(any(TaskInstanceSpec.class))).thenReturn(false);
        when(localserviceInstanceEngine.exists(anyString())).thenReturn(false);
        when(localserviceInstanceEngine.startInstance(any(LocalServiceInstanceSpec.class))).thenReturn(false);
        val mh = new ExecutorMessageHandler(applicationEngine,
                taskEngine,
                localserviceInstanceEngine,
                executorStateManager);

        assertEquals(MessageDeliveryStatus.FAILED, message.accept(mh).getStatus());
    }

    @ParameterizedTest
    @MethodSource("startMessageProvider")
    void testCreateInstanceMessageException(final ExecutorMessage message) {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val localserviceInstanceEngine = mock(LocalServiceInstanceEngine.class);
        val executorStateManager = mock(ExecutorStateManager.class);
        when(executorStateManager.currentState()).thenReturn(ExecutorState.ACTIVE);
        when(applicationEngine.exists(anyString())).thenReturn(false);
        when(applicationEngine.startInstance(any(ApplicationInstanceSpec.class)))
            .thenThrow(new IllegalStateException("Forced failure"));
        when(taskEngine.exists(anyString())).thenReturn(false);
        when(taskEngine.startInstance(any(TaskInstanceSpec.class)))
            .thenThrow(new IllegalStateException("Forced failure"));
        when(localserviceInstanceEngine.exists(anyString())).thenReturn(false);
        when(localserviceInstanceEngine.startInstance(any(LocalServiceInstanceSpec.class)))
            .thenThrow(new IllegalStateException("Forced failure"));
        val mh = new ExecutorMessageHandler(applicationEngine,
                taskEngine,
                localserviceInstanceEngine,
                executorStateManager);

        assertEquals(MessageDeliveryStatus.FAILED, message.accept(mh).getStatus());
    }

    @ParameterizedTest
    @MethodSource("stopMessageProvider")
    void testStopInstanceMessage(final ExecutorMessage message) {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val localserviceInstanceEngine = mock(LocalServiceInstanceEngine.class);
        val executorStateManager = mock(ExecutorStateManager.class);
        when(executorStateManager.currentState()).thenReturn(ExecutorState.ACTIVE);
        when(applicationEngine.stopInstance(anyString())).thenReturn(true);
        when(taskEngine.stopInstance(anyString())).thenReturn(true);
        when(localserviceInstanceEngine.stopInstance(anyString())).thenReturn(true);
        val mh = new ExecutorMessageHandler(applicationEngine,
                taskEngine,
                localserviceInstanceEngine,
                executorStateManager);

        assertEquals(MessageDeliveryStatus.ACCEPTED, message.accept(mh).getStatus());
    }

    @ParameterizedTest
    @MethodSource("stopMessageProvider")
    void testStopMessageWrongFail(final ExecutorMessage message) {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val localserviceInstanceEngine = mock(LocalServiceInstanceEngine.class);
        val executorStateManager = mock(ExecutorStateManager.class);
        when(executorStateManager.currentState()).thenReturn(ExecutorState.ACTIVE);
        when(applicationEngine.stopInstance(anyString())).thenReturn(false);
        when(taskEngine.stopInstance(anyString())).thenReturn(false);
        when(localserviceInstanceEngine.stopInstance(anyString())).thenReturn(false);
        val mh = new ExecutorMessageHandler(applicationEngine,
                                            taskEngine,
                                            localserviceInstanceEngine,
                                            executorStateManager);
        assertEquals(MessageDeliveryStatus.FAILED, message.accept(mh).getStatus());
    }

    @ParameterizedTest
    @MethodSource("stopMessageProvider")
    void testStopInstanceMessageThrow(final ExecutorMessage message) {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val localserviceInstanceEngine = mock(LocalServiceInstanceEngine.class);
        val executorStateManager = mock(ExecutorStateManager.class);
        when(executorStateManager.currentState()).thenReturn(ExecutorState.ACTIVE);
        when(applicationEngine.stopInstance(anyString())).thenThrow(new IllegalArgumentException("Forced fail"));
        when(taskEngine.stopInstance(anyString())).thenThrow(new IllegalArgumentException("Forced fail"));
        when(localserviceInstanceEngine.stopInstance(anyString())).thenThrow(new IllegalArgumentException("Forced fail"));
        val mh = new ExecutorMessageHandler(applicationEngine,
                                            taskEngine,
                                            localserviceInstanceEngine,
                                            executorStateManager);

        assertEquals(MessageDeliveryStatus.FAILED, message.accept(mh).getStatus());
    }

    @Test
    void testBlacklist() {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val localserviceInstanceEngine = mock(LocalServiceInstanceEngine.class);
        val executorStateManager = mock(ExecutorStateManager.class);
        when(executorStateManager.currentState()).thenReturn(ExecutorState.ACTIVE);
        val mh = new ExecutorMessageHandler(applicationEngine,
                                            taskEngine,
                                            localserviceInstanceEngine,
                                            executorStateManager);

        assertEquals(MessageDeliveryStatus.ACCEPTED,
                     mh.visit(new BlacklistExecutorMessage(MessageHeader.controllerRequest(),
                                                           executor())).getStatus());
    }

    @Test
    void testBlacklistFinalize() {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val localserviceInstanceEngine = mock(LocalServiceInstanceEngine.class);
        val executorStateManager = mock(ExecutorStateManager.class);
        when(executorStateManager.currentState()).thenReturn(ExecutorState.ACTIVE);
        val mh = new ExecutorMessageHandler(applicationEngine,
                                            taskEngine,
                                            localserviceInstanceEngine,
                                            executorStateManager);

        assertEquals(MessageDeliveryStatus.ACCEPTED,
                     mh.visit(new BlacklistExecutorFinalizeMessage(MessageHeader.controllerRequest(),
                                                                   executor())).getStatus());
    }

    @Test
    void testBlacklistException() {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val localserviceInstanceEngine = mock(LocalServiceInstanceEngine.class);
        val executorStateManager = mock(ExecutorStateManager.class);
        when(executorStateManager.currentState()).thenReturn(ExecutorState.ACTIVE);
        val mh = new ExecutorMessageHandler(applicationEngine,
                                            taskEngine,
                                            localserviceInstanceEngine,
                                            executorStateManager);

        doThrow(new IllegalArgumentException()).when(executorStateManager).requestBlacklist();
        assertEquals(MessageDeliveryStatus.FAILED,
                     mh.visit(new BlacklistExecutorMessage(MessageHeader.controllerRequest(),
                                                           executor())).getStatus());
        doThrow(new IllegalArgumentException()).when(executorStateManager).markBlacklisted();
        assertEquals(MessageDeliveryStatus.FAILED,
                     mh.visit(new BlacklistExecutorFinalizeMessage(MessageHeader.controllerRequest(),
                                                           executor())).getStatus());
     }

    @Test
    void testUnBlacklist() {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val localserviceInstanceEngine = mock(LocalServiceInstanceEngine.class);
        val executorStateManager = mock(ExecutorStateManager.class);
        when(executorStateManager.currentState()).thenReturn(ExecutorState.ACTIVE);
        val mh = new ExecutorMessageHandler(applicationEngine,
                                            taskEngine,
                                            localserviceInstanceEngine,
                                            executorStateManager);

        assertEquals(MessageDeliveryStatus.ACCEPTED,
                     mh.visit(new UnBlacklistExecutorMessage(MessageHeader.controllerRequest(),
                                                             executor())).getStatus());
    }

    @Test
    void testUnBlacklistException() {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val localserviceInstanceEngine = mock(LocalServiceInstanceEngine.class);
        val executorStateManager = mock(ExecutorStateManager.class);
        when(executorStateManager.currentState()).thenReturn(ExecutorState.ACTIVE);
        val mh = new ExecutorMessageHandler(applicationEngine,
                                            taskEngine,
                                            localserviceInstanceEngine,
                                            executorStateManager);

        doThrow(new IllegalArgumentException()).when(executorStateManager).unblacklist();
        assertEquals(MessageDeliveryStatus.FAILED,
                     mh.visit(new UnBlacklistExecutorMessage(MessageHeader.controllerRequest(),
                                                             executor())).getStatus());
    }

    @Test
    void testStartTaskChecksTaskEngine() {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val localserviceInstanceEngine = mock(LocalServiceInstanceEngine.class);
        val executorStateManager = mock(ExecutorStateManager.class);
        when(executorStateManager.currentState()).thenReturn(ExecutorState.ACTIVE);
        val mh = new ExecutorMessageHandler(applicationEngine,
                                            taskEngine,
                                            localserviceInstanceEngine,
                                            executorStateManager);
        when(taskEngine.exists(anyString())).thenReturn(true);
        when(applicationEngine.exists(anyString())).thenReturn(false);

        val message = new StartTaskMessage(MessageHeader.controllerRequest(),
                                           executor(),
                                           ExecutorTestingUtils.testTaskInstanceSpec());
        assertEquals(MessageDeliveryStatus.FAILED, message.accept(mh).getStatus());
        verify(taskEngine).exists(anyString());
        verify(applicationEngine, never()).exists(anyString());
    }

    @Test
    void testStartLocalServiceChecksLocalServiceEngine() {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val localserviceInstanceEngine = mock(LocalServiceInstanceEngine.class);
        val executorStateManager = mock(ExecutorStateManager.class);
        when(executorStateManager.currentState()).thenReturn(ExecutorState.ACTIVE);
        val mh = new ExecutorMessageHandler(applicationEngine,
                                            taskEngine,
                                            localserviceInstanceEngine,
                                            executorStateManager);
        when(localserviceInstanceEngine.exists(anyString())).thenReturn(true);
        when(applicationEngine.exists(anyString())).thenReturn(false);

        val message = new StartLocalServiceInstanceMessage(MessageHeader.controllerRequest(),
                                                           executor(),
                                                           ExecutorTestingUtils.testLocalServiceInstanceSpec());
        assertEquals(MessageDeliveryStatus.FAILED, message.accept(mh).getStatus());
        verify(localserviceInstanceEngine).exists(anyString());
        verify(applicationEngine, never()).exists(anyString());
    }

    @Test
    void testStartLocalServiceSucceedsWhenUnready() {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val localserviceInstanceEngine = mock(LocalServiceInstanceEngine.class);
        val executorStateManager = mock(ExecutorStateManager.class);
        when(executorStateManager.currentState()).thenReturn(ExecutorState.UNREADY);
        val mh = new ExecutorMessageHandler(applicationEngine,
                                            taskEngine,
                                            localserviceInstanceEngine,
                                            executorStateManager);
        when(localserviceInstanceEngine.exists(anyString())).thenReturn(false);
        when(localserviceInstanceEngine.startInstance(any(LocalServiceInstanceSpec.class))).thenReturn(true);

        val message = new StartLocalServiceInstanceMessage(MessageHeader.controllerRequest(),
                                                           executor(),
                                                           ExecutorTestingUtils.testLocalServiceInstanceSpec());
        assertEquals(MessageDeliveryStatus.FAILED, message.accept(mh).getStatus());
    }

    @Test
    void testStartLocalServiceFailsWhenBlacklisted() {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val localserviceInstanceEngine = mock(LocalServiceInstanceEngine.class);
        val executorStateManager = mock(ExecutorStateManager.class);
        when(executorStateManager.currentState()).thenReturn(ExecutorState.BLACKLISTED);
        val mh = new ExecutorMessageHandler(applicationEngine,
                                            taskEngine,
                                            localserviceInstanceEngine,
                                            executorStateManager);
        when(localserviceInstanceEngine.exists(anyString())).thenReturn(false);

        val message = new StartLocalServiceInstanceMessage(MessageHeader.controllerRequest(),
                                                           executor(),
                                                           ExecutorTestingUtils.testLocalServiceInstanceSpec());
        assertEquals(MessageDeliveryStatus.FAILED, message.accept(mh).getStatus());
    }

    @Test
    void testStartLocalServiceFailsWhenBlacklistRequested() {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val localserviceInstanceEngine = mock(LocalServiceInstanceEngine.class);
        val executorStateManager = mock(ExecutorStateManager.class);
        when(executorStateManager.currentState()).thenReturn(ExecutorState.BLACKLIST_REQUESTED);
        val mh = new ExecutorMessageHandler(applicationEngine,
                                            taskEngine,
                                            localserviceInstanceEngine,
                                            executorStateManager);
        when(localserviceInstanceEngine.exists(anyString())).thenReturn(false);

        val message = new StartLocalServiceInstanceMessage(MessageHeader.controllerRequest(),
                                                           executor(),
                                                           ExecutorTestingUtils.testLocalServiceInstanceSpec());
        assertEquals(MessageDeliveryStatus.FAILED, message.accept(mh).getStatus());
    }

    private static Stream<Arguments> startMessageProvider() {
        return Stream.of(
                Arguments.of(new StartInstanceMessage(MessageHeader.controllerRequest(),
                                                     executor(),
                                                     ExecutorTestingUtils.testAppInstanceSpec())),
                Arguments.of(new StartTaskMessage(MessageHeader.controllerRequest(),
                                                                                         executor(),
                                                                                         ExecutorTestingUtils.testTaskInstanceSpec())),
                Arguments.of(new  StartLocalServiceInstanceMessage(MessageHeader.controllerRequest(),
                                                                                                 executor(),
                                                                                                 ExecutorTestingUtils.testLocalServiceInstanceSpec()))
        );
    }

    private static Stream<Arguments> stopMessageProvider() {
        return Stream.of(
                Arguments.of(new StopInstanceMessage(MessageHeader.controllerRequest(),
                                                     executor(),
                                                     "instanceId")),
                Arguments.of(new StopTaskMessage(MessageHeader.controllerRequest(),
                                                     executor(),"taskInstanceId")),
                Arguments.of(new StopLocalServiceInstanceMessage(MessageHeader.controllerRequest(),
                                                     executor(),"localServiceInstanceId"))
        );
    }

}
