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
                                                                                           new AvailableMemory(Map.of(), Map.of())),
                                                              null));
        assertEquals(MessageDeliveryStatus.ACCEPTED, res.getStatus());
    }
}