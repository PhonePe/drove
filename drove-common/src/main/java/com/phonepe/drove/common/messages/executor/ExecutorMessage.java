package com.phonepe.drove.common.messages.executor;

import com.phonepe.drove.internalmodels.ExecutorMessageType;
import com.phonepe.drove.internalmodels.Message;
import com.phonepe.drove.internalmodels.MessageHeader;

/**
 *
 */
public abstract class ExecutorMessage extends Message<ExecutorMessageType> {

    protected ExecutorMessage(ExecutorMessageType type, MessageHeader header) {
        super(type, header);
    }

    public abstract <T> T accept(final ExecutorMessageVisitor<T> visitor);
}
