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

package com.phonepe.drove.executor.engine;

import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.executor.BlacklistExecutorMessage;
import com.phonepe.drove.common.model.executor.StartInstanceMessage;
import com.phonepe.drove.common.model.executor.StopInstanceMessage;
import com.phonepe.drove.common.model.executor.UnBlacklistExecutorMessage;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.statemachine.BlacklistingManager;
import lombok.val;
import org.junit.jupiter.api.Test;

import static com.phonepe.drove.common.CommonTestUtils.executor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 *
 */
class ExecutorMessageHandlerTest {

    @Test
    void testCreateInstanceMessage() {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val blacklistingManager = mock(BlacklistingManager.class);
        val mh = new ExecutorMessageHandler(applicationEngine, taskEngine, blacklistingManager);
        when(applicationEngine.exists(anyString())).thenReturn(false);
        when(applicationEngine.startInstance(any(ApplicationInstanceSpec.class))).thenReturn(true);
        assertEquals(MessageDeliveryStatus.ACCEPTED,
                     mh.visit(new StartInstanceMessage(MessageHeader.controllerRequest(),
                                                       executor(),
                                                       ExecutorTestingUtils.testAppInstanceSpec()))
                             .getStatus());
    }

    @Test
    void testCreateInstanceMessageExists() {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val blacklistingManager = mock(BlacklistingManager.class);
        val mh = new ExecutorMessageHandler(applicationEngine, taskEngine, blacklistingManager);

        when(applicationEngine.exists(anyString())).thenReturn(true);
        assertEquals(MessageDeliveryStatus.FAILED,
                     mh.visit(new StartInstanceMessage(MessageHeader.controllerRequest(),
                                                       executor(),
                                                       ExecutorTestingUtils.testAppInstanceSpec()))
                             .getStatus());
    }

    @Test
    void testCreateInstanceMessageFail() {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val blacklistingManager = mock(BlacklistingManager.class);
        val mh = new ExecutorMessageHandler(applicationEngine, taskEngine, blacklistingManager);

        when(applicationEngine.exists(anyString())).thenReturn(false);
        when(applicationEngine.startInstance(any(ApplicationInstanceSpec.class))).thenReturn(false);
        assertEquals(MessageDeliveryStatus.FAILED,
                     mh.visit(new StartInstanceMessage(MessageHeader.controllerRequest(),
                                                       executor(),
                                                       ExecutorTestingUtils.testAppInstanceSpec()))
                             .getStatus());
    }

    @Test
    void testCreateInstanceMessageException() {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val blacklistingManager = mock(BlacklistingManager.class);
        val mh = new ExecutorMessageHandler(applicationEngine, taskEngine, blacklistingManager);

        when(applicationEngine.exists(anyString())).thenReturn(false);
        when(applicationEngine.startInstance(any(ApplicationInstanceSpec.class))).thenThrow(new IllegalStateException("Forced failure"));
        assertEquals(MessageDeliveryStatus.FAILED,
                     mh.visit(new StartInstanceMessage(MessageHeader.controllerRequest(),
                                                       executor(),
                                                       ExecutorTestingUtils.testAppInstanceSpec()))
                             .getStatus());
    }

    @Test
    void testStopInstanceMessage() {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val blacklistingManager = mock(BlacklistingManager.class);
        val mh = new ExecutorMessageHandler(applicationEngine, taskEngine, blacklistingManager);

        when(applicationEngine.exists(anyString())).thenReturn(true);
        when(applicationEngine.stopInstance(anyString())).thenReturn(true);
        assertEquals(MessageDeliveryStatus.ACCEPTED,
                     mh.visit(new StopInstanceMessage(MessageHeader.controllerRequest(),
                                                      executor(),
                                                      "blah"))
                             .getStatus());
    }

    @Test
    void testStopInstanceMessageWrongId() {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val blacklistingManager = mock(BlacklistingManager.class);
        val mh = new ExecutorMessageHandler(applicationEngine, taskEngine, blacklistingManager);

        when(applicationEngine.stopInstance(anyString())).thenReturn(false);
        assertEquals(MessageDeliveryStatus.FAILED,
                     mh.visit(new StopInstanceMessage(MessageHeader.controllerRequest(),
                                                      executor(),
                                                      "blah"))
                             .getStatus());
    }

    @Test
    void testStopInstanceMessageFail() {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val blacklistingManager = mock(BlacklistingManager.class);
        val mh = new ExecutorMessageHandler(applicationEngine, taskEngine, blacklistingManager);

        when(applicationEngine.exists(anyString())).thenReturn(true);
        when(applicationEngine.stopInstance(anyString())).thenReturn(false);
        assertEquals(MessageDeliveryStatus.FAILED,
                     mh.visit(new StopInstanceMessage(MessageHeader.controllerRequest(),
                                                      executor(),
                                                      "blah"))
                             .getStatus());
    }

    @Test
    void testStopInstanceMessageThrow() {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val blacklistingManager = mock(BlacklistingManager.class);
        val mh = new ExecutorMessageHandler(applicationEngine, taskEngine, blacklistingManager);

        when(applicationEngine.exists(anyString())).thenReturn(true);
        when(applicationEngine.stopInstance(anyString())).thenThrow(new IllegalArgumentException("Forced fail"));
        assertEquals(MessageDeliveryStatus.FAILED,
                     mh.visit(new StopInstanceMessage(MessageHeader.controllerRequest(),
                                                      executor(),
                                                      "blah"))
                             .getStatus());
    }

    @Test
    void testBlacklist() {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val blacklistingManager = mock(BlacklistingManager.class);
        val mh = new ExecutorMessageHandler(applicationEngine, taskEngine, blacklistingManager);

        assertEquals(MessageDeliveryStatus.ACCEPTED,
                     mh.visit(new BlacklistExecutorMessage(MessageHeader.controllerRequest(),
                                                           executor())).getStatus());
    }

    @Test
    void testBlacklistException() {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val blacklistingManager = mock(BlacklistingManager.class);
        val mh = new ExecutorMessageHandler(applicationEngine, taskEngine, blacklistingManager);

        doThrow(new IllegalArgumentException()).when(blacklistingManager).blacklist();
        assertEquals(MessageDeliveryStatus.FAILED,
                     mh.visit(new BlacklistExecutorMessage(MessageHeader.controllerRequest(),
                                                           executor())).getStatus());
    }

    @Test
    void testUnBlacklist() {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val blacklistingManager = mock(BlacklistingManager.class);
        val mh = new ExecutorMessageHandler(applicationEngine, taskEngine, blacklistingManager);

        assertEquals(MessageDeliveryStatus.ACCEPTED,
                     mh.visit(new UnBlacklistExecutorMessage(MessageHeader.controllerRequest(),
                                                             executor())).getStatus());
    }

    @Test
    void testUnBlacklistException() {
        val applicationEngine = mock(ApplicationInstanceEngine.class);
        val taskEngine = mock(TaskInstanceEngine.class);
        val blacklistingManager = mock(BlacklistingManager.class);
        val mh = new ExecutorMessageHandler(applicationEngine, taskEngine, blacklistingManager);

        doThrow(new IllegalArgumentException()).when(blacklistingManager).unblacklist();
        assertEquals(MessageDeliveryStatus.FAILED,
                     mh.visit(new UnBlacklistExecutorMessage(MessageHeader.controllerRequest(),
                                                           executor())).getStatus());
    }
}