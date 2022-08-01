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
public class StopInstanceMessage extends ExecutorMessage {

    String instanceId;

    public StopInstanceMessage(
            MessageHeader header,
            ExecutorAddress address,
            String instanceId) {
        super(ExecutorMessageType.STOP_INSTANCE, header, address);
        this.instanceId = instanceId;
    }

    @Override
    public <T> T accept(ExecutorMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }
}