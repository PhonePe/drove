package com.phonepe.drove.common.net;

import com.phonepe.drove.common.model.Message;
import com.phonepe.drove.common.model.MessageResponse;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.val;

import java.util.function.Consumer;

/**
 *
 */
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
    public MessageResponse send(SendMessage message, Consumer<MessageResponse> responseConsumer) {
        val response = messageSender.send(message);
        responseReceived.dispatch(response);
        if(null != responseConsumer) {
            responseConsumer.accept(response);
        }
        return response;
    }

    @Override
    public MessageResponse receive(ReceiveMessage message) {
        return handleReceivedMessage(message);
    }

    protected abstract MessageResponse handleReceivedMessage(final ReceiveMessage message);

}
