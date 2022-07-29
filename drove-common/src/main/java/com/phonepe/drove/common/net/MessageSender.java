package com.phonepe.drove.common.net;

import com.phonepe.drove.common.model.Message;
import com.phonepe.drove.common.model.MessageResponse;

/**
 *
 */
@FunctionalInterface
@SuppressWarnings("java:S119")
public interface MessageSender<SendMessageType extends Enum<SendMessageType>, SendMessage extends Message<SendMessageType>> {
    MessageResponse send(final SendMessage message);
}
