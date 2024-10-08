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

package com.phonepe.drove.common.net;

import com.phonepe.drove.common.model.Message;
import com.phonepe.drove.common.model.MessageResponse;
import io.appform.functionmetrics.MonitoredFunction;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.val;

import java.util.function.Consumer;

/**
 *
 */
@SuppressWarnings("java:S119")
public abstract class ThreadedCommunicator<
        SendMessageType extends Enum<SendMessageType>,
        ReceiveMessageType extends Enum<ReceiveMessageType>,
        SendMessage extends Message<SendMessageType>,
        ReceiveMessage extends Message<ReceiveMessageType>>
        implements Communicator<SendMessageType, ReceiveMessageType, SendMessage, ReceiveMessage> {
    private final ConsumingSyncSignal<MessageResponse> responseReceived;
    private final MessageSender<SendMessageType, SendMessage> messageSender;

    protected ThreadedCommunicator(MessageSender<SendMessageType, SendMessage> messageSender) {
        this.messageSender = messageSender;
        responseReceived = new ConsumingSyncSignal<>();
    }

    @Override
    public ConsumingSyncSignal<MessageResponse> onResponse() {
        return responseReceived;
    }

    @Override
    @MonitoredFunction
    public MessageResponse send(SendMessage message, Consumer<MessageResponse> responseConsumer) {
        val response = messageSender.send(message);
        responseReceived.dispatch(response);
        if(null != responseConsumer) {
            responseConsumer.accept(response);
        }
        return response;
    }

    @Override
    @MonitoredFunction
    public MessageResponse receive(ReceiveMessage message) {
        return handleReceivedMessage(message);
    }

    protected abstract MessageResponse handleReceivedMessage(final ReceiveMessage message);

}
