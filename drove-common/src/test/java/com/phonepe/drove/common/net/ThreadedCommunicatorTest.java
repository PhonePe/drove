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

package com.phonepe.drove.common.net;

import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.model.*;
import com.phonepe.drove.common.model.controller.ControllerMessage;
import com.phonepe.drove.common.model.controller.ExecutorSnapshotMessage;
import com.phonepe.drove.common.model.executor.BlacklistExecutorMessage;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.model.executor.ExecutorMessage;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import lombok.Getter;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class ThreadedCommunicatorTest extends AbstractTestBase {
    private static class TestCommunicator extends ThreadedCommunicator<ExecutorMessageType, ControllerMessageType,
            ExecutorMessage, ControllerMessage> {
        @Getter
        private final AtomicBoolean received = new AtomicBoolean();

        public TestCommunicator(MessageSender<ExecutorMessageType, ExecutorMessage> messageSender) {
            super(messageSender);
        }

        @Override
        public MessageResponse send(ExecutorMessage message) {
            return super.send(message);
        }

        @Override
        protected MessageResponse handleReceivedMessage(ControllerMessage message) {
            received.set(true);
            return new MessageResponse(message.getHeader(), MessageDeliveryStatus.ACCEPTED);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testComms() {
        val sender = (MessageSender<ExecutorMessageType, ExecutorMessage>) mock(MessageSender.class);
        val comm = new TestCommunicator(sender);
        val sent1 = new AtomicBoolean();
        val sent2 = new AtomicBoolean();
        when(sender.send(any(ExecutorMessage.class)))
                .thenAnswer(invocationOnMock -> {
                    val mgs = invocationOnMock.getArgument(0, ExecutorMessage.class);
                    return new MessageResponse(mgs.getHeader(), MessageDeliveryStatus.ACCEPTED);
                });
        comm.onResponse().connect(r -> sent1.set(r.getStatus().equals(MessageDeliveryStatus.ACCEPTED)));
        comm.send(new BlacklistExecutorMessage(MessageHeader.executorRequest(),
                                               new ExecutorAddress("Test", "localhost", 8080, NodeTransportType.HTTP)),
                  r -> sent2.set(r.getStatus().equals(MessageDeliveryStatus.ACCEPTED)));
        assertTrue(sent1.get());
        assertTrue(sent2.get());
        assertEquals(MessageDeliveryStatus.ACCEPTED,
                     comm.receive(new ExecutorSnapshotMessage(MessageHeader.controllerRequest(), null)).getStatus());
        assertTrue(comm.getReceived().get());
    }
}