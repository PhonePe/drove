package com.phonepe.drove.executor.engine;

import com.phonepe.drove.common.ThreadedCommunicator;
import com.phonepe.drove.internalmodels.controller.ControllerMessage;
import com.phonepe.drove.internalmodels.executor.ExecutorMessage;
import com.phonepe.drove.internalmodels.*;
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
