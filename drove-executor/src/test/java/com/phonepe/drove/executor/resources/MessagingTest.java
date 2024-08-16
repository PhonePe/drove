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

package com.phonepe.drove.executor.resources;

import com.phonepe.drove.auth.model.DroveExternalUser;
import com.phonepe.drove.auth.model.DroveUserRole;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.executor.BlacklistExecutorMessage;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.model.executor.ExecutorMessage;
import com.phonepe.drove.executor.engine.ExecutorCommunicator;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
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
class MessagingTest {
    @Test
    void testMessageApi() {
        val comm = mock(ExecutorCommunicator.class);
        val r = new Messaging(comm);

        when(comm.receive(any(ExecutorMessage.class))).thenAnswer(
                (Answer<MessageResponse>) invocationOnMock -> new MessageResponse(invocationOnMock.getArgument(0,
                                                                                                               ExecutorMessage.class)
                                                                                          .getHeader(),
                                                                                  MessageDeliveryStatus.ACCEPTED));
        val res = r.receiveCommand(new DroveExternalUser("test", DroveUserRole.EXTERNAL_READ_WRITE, null),
                         new BlacklistExecutorMessage(MessageHeader.controllerRequest(),
                                                      new ExecutorAddress("E1",
                                                                          "localhost",
                                                                          8080,
                                                                          NodeTransportType.HTTP)));
        assertEquals(MessageDeliveryStatus.ACCEPTED, res.getStatus());
    }
}