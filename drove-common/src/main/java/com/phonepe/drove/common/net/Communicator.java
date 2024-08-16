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
import io.appform.signals.signals.ConsumingSyncSignal;

import java.util.function.Consumer;

/**
 *
 */
@SuppressWarnings("java:S119")
public interface Communicator<
        SendMessageType extends Enum<SendMessageType>,
        ReceiveMessageType extends Enum<ReceiveMessageType>,
        SendMessage extends Message<SendMessageType>,
        ReceiveMessage extends Message<ReceiveMessageType>> {
    ConsumingSyncSignal<MessageResponse> onResponse();
    /**
     * Send message to remote
     * @param message the actual message received
     */
    default MessageResponse send(final SendMessage message) {
        return send(message, null);
    }

    MessageResponse send(final SendMessage message, final Consumer<MessageResponse> onComplete);

    MessageResponse receive(final ReceiveMessage message);
}
