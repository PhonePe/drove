package com.phonepe.drove.executor.resources;

import com.phonepe.drove.common.auth.model.DroveExternalUser;
import com.phonepe.drove.common.auth.model.DroveUserRole;
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