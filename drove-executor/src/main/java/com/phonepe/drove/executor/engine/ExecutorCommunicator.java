package com.phonepe.drove.executor.engine;

import com.phonepe.drove.common.ThreadedCommunicator;
import com.phonepe.drove.common.model.ControllerMessageType;
import com.phonepe.drove.common.model.ExecutorMessageType;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.controller.ControllerMessage;
import com.phonepe.drove.common.model.executor.ExecutorMessage;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 *
 */
@Slf4j
@Singleton
public class ExecutorCommunicator extends ThreadedCommunicator<ControllerMessageType, ExecutorMessageType, ControllerMessage, ExecutorMessage> {
    private final InstanceEngine engine;

    @Inject
    public ExecutorCommunicator(
            InstanceEngine engine,
            ExecutorMessageSender messageSender) {
        this.engine = engine;
        onMessageReady().connect(messageSender::sendRemoteMessage);
    }

    @Override
    protected MessageResponse handleReceivedMessage(ExecutorMessage message) {
        try {
            return engine.handleMessage(message);
        }
        catch (Exception e) {
            log.error("Error handling message: ", e);
            return new MessageResponse(message.getHeader(), MessageDeliveryStatus.FAILED);
        }
    }
}
