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

package com.phonepe.drove.executor;

import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.model.*;
import com.phonepe.drove.common.model.controller.ExecutorSnapshotMessage;
import com.phonepe.drove.common.model.executor.BlacklistExecutorMessage;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.model.executor.ExecutorMessage;
import com.phonepe.drove.executor.engine.ApplicationInstanceEngine;
import com.phonepe.drove.executor.engine.ExecutorCommunicator;
import com.phonepe.drove.executor.engine.ExecutorMessageHandler;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
@Slf4j
class ExecutorCommunicatorTest extends AbstractTestBase {
    @Test
    void testComms() {
        val engine = mock(ApplicationInstanceEngine.class);
        val messageHandler = mock(ExecutorMessageHandler.class);
        when(messageHandler.visit(any(BlacklistExecutorMessage.class)))
                .thenAnswer((Answer<MessageResponse>) mock -> {
                    val param = (ExecutorMessage) mock.getArguments()[0];
                    assertEquals(ExecutorMessageType.BLACKLIST, param.getType());
                    return new MessageResponse(param.getHeader(), MessageDeliveryStatus.ACCEPTED);
                });

        val comm = new ExecutorCommunicator(message -> {
            assertEquals(ControllerMessageType.EXECUTOR_SNAPSHOT, message.getType());
            return new MessageResponse(message.getHeader(), MessageDeliveryStatus.ACCEPTED);
        }, messageHandler);
        comm.onResponse().connect(message -> assertEquals(MessageDeliveryStatus.ACCEPTED, message.getStatus()));

        assertEquals(MessageDeliveryStatus.ACCEPTED,
                     comm.send(new ExecutorSnapshotMessage(MessageHeader.executorRequest(), null)).getStatus());
        assertEquals(MessageDeliveryStatus.ACCEPTED,
                     comm.receive(new BlacklistExecutorMessage(MessageHeader.controllerRequest(),
                                                               new ExecutorAddress("test", "h", 3000, NodeTransportType.HTTP))).getStatus());
    }

    @Test
    void testCommsFailure() {
        val engine = mock(ApplicationInstanceEngine.class);
        val messageHandler = mock(ExecutorMessageHandler.class);
        when(messageHandler.visit(any(BlacklistExecutorMessage.class)))
                .thenThrow(new IllegalArgumentException());
        val comm = new ExecutorCommunicator(
                message -> new MessageResponse(message.getHeader(),
                                                                           MessageDeliveryStatus.ACCEPTED),
                                            messageHandler);
        assertEquals(MessageDeliveryStatus.FAILED,
                     comm.receive(new BlacklistExecutorMessage(MessageHeader.controllerRequest(),
                                                               new ExecutorAddress("test", "h", 3000, NodeTransportType.HTTP))).getStatus());
    }
}