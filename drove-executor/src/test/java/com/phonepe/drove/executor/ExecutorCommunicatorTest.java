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