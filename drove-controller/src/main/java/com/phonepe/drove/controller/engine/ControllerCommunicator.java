package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.ThreadedCommunicator;
import com.phonepe.drove.controller.statedb.StateDB;
import com.phonepe.drove.internalmodels.ControllerMessageType;
import com.phonepe.drove.internalmodels.ExecutorMessageType;
import com.phonepe.drove.internalmodels.MessageResponse;
import com.phonepe.drove.internalmodels.controller.ControllerMessage;
import com.phonepe.drove.internalmodels.executor.ExecutorMessage;

import javax.inject.Inject;

/**
 *
 */
public class ControllerCommunicator extends ThreadedCommunicator<ExecutorMessageType, ControllerMessageType, ExecutorMessage, ControllerMessage> {
    private final StateDB stateDB;

    @Inject
    public ControllerCommunicator(StateDB stateDB) {
        this.stateDB = stateDB;
    }

    @Override
    protected MessageResponse handleReceivedMessage(ControllerMessage message) {
        return message.accept(new ControllerMessageHandler(stateDB));
    }
}
