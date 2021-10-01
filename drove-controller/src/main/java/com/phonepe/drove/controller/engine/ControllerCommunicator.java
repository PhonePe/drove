package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.ThreadedCommunicator;
import com.phonepe.drove.controller.statedb.ExecutorStateDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.common.model.ControllerMessageType;
import com.phonepe.drove.common.model.ExecutorMessageType;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.controller.ControllerMessage;
import com.phonepe.drove.common.model.executor.ExecutorMessage;

import javax.inject.Inject;

/**
 *
 */
public class ControllerCommunicator extends ThreadedCommunicator<ExecutorMessageType, ControllerMessageType, ExecutorMessage, ControllerMessage> {
    private final ExecutorStateDB executorStateDB;
    private final ApplicationStateDB stateDB;

    @Inject
    public ControllerCommunicator(ExecutorStateDB executorStateDB, ApplicationStateDB stateDB) {
        this.executorStateDB = executorStateDB;
        this.stateDB = stateDB;
    }

    @Override
    protected MessageResponse handleReceivedMessage(ControllerMessage message) {
        return message.accept(new ControllerMessageHandler(executorStateDB, stateDB));
    }
}
