package com.phonepe.drove.common.messages.executor;

import com.phonepe.drove.internalmodels.ExecutorMessageType;
import com.phonepe.drove.internalmodels.MessageHeader;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class StopInstanceMessage extends ExecutorMessage {

    String instanceId;

    public StopInstanceMessage(MessageHeader header, String instanceId) {
        super(ExecutorMessageType.STOP_INSTANCE, header);
        this.instanceId = instanceId;
    }

    @Override
    public <T> T accept(ExecutorMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
