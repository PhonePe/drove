package com.phonepe.drove.common;

import com.phonepe.drove.internalmodels.Message;
import com.phonepe.drove.internalmodels.MessageResponse;
import io.appform.signals.signals.ConsumingSyncSignal;
import io.appform.signals.signals.GeneratingSyncSignal;

/**
 *
 */
public interface Communicator<
        SendMessageType extends Enum<SendMessageType>,
        ReceiveMessageType extends Enum<ReceiveMessageType>,
        SendMessage extends Message<SendMessageType>,
        ReceiveMessage extends Message<ReceiveMessageType>> {
    ConsumingSyncSignal<MessageResponse> onResponse();

    GeneratingSyncSignal<SendMessage, MessageResponse> onMessageReady();

    /**
     * Send message to remote
     * @param message the actual message received
     */
    void send(final SendMessage message);

    void receive(final ReceiveMessage message);
}
