package com.phonepe.drove.common.net;

import com.phonepe.drove.common.model.Message;
import com.phonepe.drove.common.model.MessageResponse;
import io.appform.signals.signals.ConsumingSyncSignal;

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
    void send(final SendMessage message);

    MessageResponse receive(final ReceiveMessage message);
}
