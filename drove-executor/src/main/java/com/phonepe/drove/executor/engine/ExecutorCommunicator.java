package com.phonepe.drove.executor.engine;

import com.phonepe.drove.common.model.ControllerMessageType;
import com.phonepe.drove.common.model.ExecutorMessageType;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.controller.ControllerMessage;
import com.phonepe.drove.common.model.executor.ExecutorMessage;
import com.phonepe.drove.common.net.MessageSender;
import com.phonepe.drove.common.net.ThreadedCommunicator;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 *
 */
@Slf4j
@Singleton
public class ExecutorCommunicator extends ThreadedCommunicator<ControllerMessageType, ExecutorMessageType, ControllerMessage, ExecutorMessage> {
    private final ExecutorMessageHandler messageHandler;

    @Inject
    public ExecutorCommunicator(
            MessageSender<ControllerMessageType, ControllerMessage> messageSender,
            ExecutorMessageHandler messageHandler) {
        super(messageSender);
        this.messageHandler = messageHandler;
    }

    @Override
    protected MessageResponse handleReceivedMessage(ExecutorMessage message) {
        try {
            return message.accept(messageHandler);
        }
        catch (Exception e) {
            log.error("Error handling message: ", e);
            return new MessageResponse(message.getHeader(), MessageDeliveryStatus.FAILED);
        }
    }
}