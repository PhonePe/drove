package com.phonepe.drove.common.model.executor;

import com.phonepe.drove.common.model.ExecutorMessageType;
import com.phonepe.drove.common.model.MessageHeader;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class UnBlacklistExecutorMessage extends ExecutorMessage {

    public UnBlacklistExecutorMessage(
            MessageHeader header,
            ExecutorAddress address) {
        super(ExecutorMessageType.UNBLACKLIST, header, address);
    }

    @Override
    public <T> T accept(ExecutorMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
