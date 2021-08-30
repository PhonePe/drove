package com.phonepe.drove.common;

import com.phonepe.drove.internalmodels.Message;
import com.phonepe.drove.internalmodels.MessageResponse;
import io.appform.signals.signals.ConsumingSyncSignal;
import io.appform.signals.signals.GeneratingParallelSignal;
import io.appform.signals.signals.GeneratingSyncSignal;

/**
 *
 */
public abstract class ThreadedCommunicator<
        SendMessageType extends Enum<SendMessageType>,
        ReceiveMessageType extends Enum<ReceiveMessageType>,
        SendMessage extends Message<SendMessageType>,
        ReceiveMessage extends Message<ReceiveMessageType>>
        implements Communicator<SendMessageType, ReceiveMessageType, SendMessage, ReceiveMessage> {
    private final GeneratingSyncSignal<SendMessage, MessageResponse> messageReady;
    private final ConsumingSyncSignal<MessageResponse> responseReceived;
    private final GeneratingParallelSignal<ReceiveMessage, MessageResponse> messageReceived;

    protected ThreadedCommunicator() {
        messageReady = new GeneratingSyncSignal<>();
        responseReceived = new ConsumingSyncSignal<>();
        messageReceived = new GeneratingParallelSignal<>();
        messageReceived.connect(this::handleReceivedMessage);
    }

    @Override
    public ConsumingSyncSignal<MessageResponse> onResponse() {
        return responseReceived;
    }

    @Override
    public GeneratingSyncSignal<SendMessage, MessageResponse> onMessageReady() {
        return messageReady;
    }

    @Override
    public void send(SendMessage message) {
        responseReceived.dispatch(messageReady.dispatch(message));
    }

    @Override
    public void receive(ReceiveMessage message) {
        messageReceived.dispatch(message);
    }

    protected abstract MessageResponse handleReceivedMessage(final ReceiveMessage message);
}
