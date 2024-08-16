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

package com.phonepe.drove.controller.resources;

import com.phonepe.drove.auth.model.DroveExternalUser;
import com.phonepe.drove.auth.model.DroveUserRole;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.controller.ControllerMessage;
import com.phonepe.drove.common.model.controller.TaskStateReportMessage;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.resources.PhysicalLayout;
import com.phonepe.drove.models.info.resources.available.AvailableCPU;
import com.phonepe.drove.models.info.resources.available.AvailableMemory;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.util.Map;

import static com.phonepe.drove.controller.ControllerTestUtils.EXECUTOR_ID;
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
        val comm = mock(ControllerCommunicator.class);
        val r = new Messaging(comm);

        when(comm.receive(any(ControllerMessage.class))).thenAnswer(
                (Answer<MessageResponse>) invocationOnMock ->
                        new MessageResponse(invocationOnMock.getArgument(0, ControllerMessage.class).getHeader(),
                                            MessageDeliveryStatus.ACCEPTED));
        val res = r.receiveCommand(new DroveExternalUser("test", DroveUserRole.EXTERNAL_READ_WRITE, null),
                                   new TaskStateReportMessage(MessageHeader.controllerRequest(),
                                                              new ExecutorResourceSnapshot(EXECUTOR_ID,
                                                                                           new AvailableCPU(Map.of(), Map.of()),
                                                                                           new AvailableMemory(Map.of(), Map.of()),
                                                                                           new PhysicalLayout(Map.of(), Map.of())),
                                                              null));
        assertEquals(MessageDeliveryStatus.ACCEPTED, res.getStatus());
    }
}