package com.phonepe.drove.common.model.executor;

import com.phonepe.drove.common.model.ExecutorMessageType;
import com.phonepe.drove.common.model.Message;
import com.phonepe.drove.common.model.MessageHeader;
import lombok.Data;

/**
 *
 */
@Data
public abstract class ExecutorMessage extends Message<ExecutorMessageType> {

    private final ExecutorAddress address;

    protected ExecutorMessage(
            ExecutorMessageType type,
            MessageHeader header,
            ExecutorAddress address) {
        super(type, header);
        this.address = address;
    }

    public abstract <T> T accept(final ExecutorMessageVisitor<T> visitor);
}
