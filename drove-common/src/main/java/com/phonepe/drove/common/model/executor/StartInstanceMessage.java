package com.phonepe.drove.common.model.executor;

import com.phonepe.drove.common.model.ExecutorMessageType;
import com.phonepe.drove.common.model.InstanceSpec;
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
public class StartInstanceMessage extends ExecutorMessage {
    InstanceSpec spec;

    public StartInstanceMessage(
            MessageHeader header,
            ExecutorAddress address,
            InstanceSpec spec) {
        super(ExecutorMessageType.START_INSTANCE, header, address);
        this.spec = spec;
    }

    @Override
    public <T> T accept(ExecutorMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
