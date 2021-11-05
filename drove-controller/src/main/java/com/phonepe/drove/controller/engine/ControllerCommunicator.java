package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.net.MessageSender;
import com.phonepe.drove.common.net.ThreadedCommunicator;
import com.phonepe.drove.controller.statedb.ExecutorStateDB;
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
    private final StateUpdater stateUpdater;

    @Inject
    public ControllerCommunicator(
            ExecutorStateDB executorStateDB,
            StateUpdater stateUpdater,
            MessageSender<ExecutorMessageType, ExecutorMessage> messageSender) {
        super(messageSender);
        this.executorStateDB = executorStateDB;
        this.stateUpdater = stateUpdater;
    }

    @Override
    protected MessageResponse handleReceivedMessage(ControllerMessage message) {
        return message.accept(new ControllerMessageHandler(executorStateDB, stateUpdater));
    }
}
