package com.phonepe.drove.common.model.executor;

import com.phonepe.drove.common.model.ExecutorMessageType;
import com.phonepe.drove.common.model.Message;
import com.phonepe.drove.common.model.MessageHeader;

/**
 *
 */
public abstract class ExecutorMessage extends Message<ExecutorMessageType> {

    protected ExecutorMessage(ExecutorMessageType type, MessageHeader header) {
        super(type, header);
    }

    public abstract <T> T accept(final ExecutorMessageVisitor<T> visitor);
}
