/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

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
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 *
 */
@Singleton
@Slf4j
public class ControllerCommunicator extends ThreadedCommunicator<ExecutorMessageType, ControllerMessageType, ExecutorMessage, ControllerMessage> {
    private final StateUpdater stateUpdater;
    private final LeadershipEnsurer leadershipEnsurer;

    @Inject
    public ControllerCommunicator(
            StateUpdater stateUpdater,
            MessageSender<ExecutorMessageType, ExecutorMessage> messageSender,
            final LeadershipEnsurer leadershipEnsurer) {
        super(messageSender);
        this.stateUpdater = stateUpdater;
        this.leadershipEnsurer = leadershipEnsurer;
    }

    @Override
    protected MessageResponse handleReceivedMessage(ControllerMessage message) {
        if(!leadershipEnsurer.isLeader()) {
            return new MessageResponse(message.getHeader(), MessageDeliveryStatus.REJECTED);
        }
        try {
            return message.accept(new ControllerMessageHandler(stateUpdater));
        }
        catch (Exception e) {
            log.error("Error handling message: " + e.getMessage(), e);
            return new MessageResponse(message.getHeader(), MessageDeliveryStatus.FAILED);
        }
    }
}
