package com.phonepe.drove.common.net;

import com.phonepe.drove.common.model.Message;
import com.phonepe.drove.common.model.MessageResponse;
import io.appform.signals.signals.ConsumingSyncSignal;

import java.util.function.Consumer;

/**
 *
 */
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
