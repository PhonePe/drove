package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.model.ControllerMessageType;
import com.phonepe.drove.common.model.ExecutorMessageType;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.controller.ControllerMessage;
import com.phonepe.drove.common.model.executor.ExecutorMessage;
import com.phonepe.drove.common.net.MessageSender;
import com.phonepe.drove.common.net.ThreadedCommunicator;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 *
 */
@Singleton
public class ControllerCommunicator extends ThreadedCommunicator<ExecutorMessageType, ControllerMessageType, ExecutorMessage, ControllerMessage> {
    private final StateUpdater stateUpdater;
    private final LeadershipEnsurer leadershipEnsurer;
    private final DroveEventBus eventBus;

    @Inject
    public ControllerCommunicator(
            StateUpdater stateUpdater,
            MessageSender<ExecutorMessageType, ExecutorMessage> messageSender,
            final LeadershipEnsurer leadershipEnsurer, DroveEventBus eventBus) {
        super(messageSender);
        this.stateUpdater = stateUpdater;
        this.leadershipEnsurer = leadershipEnsurer;
        this.eventBus = eventBus;
    }

    @Override
    protected MessageResponse handleReceivedMessage(ControllerMessage message) {
        if(!leadershipEnsurer.isLeader()) {
            return new MessageResponse(message.getHeader(), MessageDeliveryStatus.REJECTED);
        }
        return message.accept(new ControllerMessageHandler(stateUpdater, eventBus));
    }
}
