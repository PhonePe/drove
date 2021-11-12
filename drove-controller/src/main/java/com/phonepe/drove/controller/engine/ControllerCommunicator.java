package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.model.ControllerMessageType;
import com.phonepe.drove.common.model.ExecutorMessageType;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.controller.ControllerMessage;
import com.phonepe.drove.common.model.executor.ExecutorMessage;
import com.phonepe.drove.common.net.MessageSender;
import com.phonepe.drove.common.net.ThreadedCommunicator;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.controller.statedb.ExecutorStateDB;

import javax.inject.Inject;

/**
 *
 */
public class ControllerCommunicator extends ThreadedCommunicator<ExecutorMessageType, ControllerMessageType, ExecutorMessage, ControllerMessage> {
    private final ExecutorStateDB executorStateDB;
    private final StateUpdater stateUpdater;
    private final LeadershipEnsurer leadershipEnsurer;

    @Inject
    public ControllerCommunicator(
            ExecutorStateDB executorStateDB,
            StateUpdater stateUpdater,
            MessageSender<ExecutorMessageType, ExecutorMessage> messageSender,
            final LeadershipEnsurer leadershipEnsurer) {
        super(messageSender);
        this.executorStateDB = executorStateDB;
        this.stateUpdater = stateUpdater;
        this.leadershipEnsurer = leadershipEnsurer;
    }

    @Override
    protected MessageResponse handleReceivedMessage(ControllerMessage message) {
        if(!leadershipEnsurer.isLeader()) {
            return new MessageResponse(message.getHeader(), MessageDeliveryStatus.REJECTED);
        }
        return message.accept(new ControllerMessageHandler(executorStateDB, stateUpdater));
    }
}
