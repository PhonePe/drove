package com.phonepe.drove.executor;

import com.phonepe.drove.common.ThreadedCommunicator;
import com.phonepe.drove.common.messages.controller.ControllerMessage;
import com.phonepe.drove.common.messages.executor.ExecutorMessage;
import com.phonepe.drove.internalmodels.*;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Slf4j
public class ExecutorCommunicator extends ThreadedCommunicator<ControllerMessageType, ExecutorMessageType, ControllerMessage, ExecutorMessage> {
    private final InstanceEngine engine;

    public ExecutorCommunicator(InstanceEngine engine) {
        this.engine = engine;
        engine.setCommunicator(this);
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
