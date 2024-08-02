/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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
