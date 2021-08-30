package com.phonepe.drove.common.messages.executor;

import com.phonepe.drove.internalmodels.ExecutorMessageType;
import com.phonepe.drove.internalmodels.InstanceSpec;
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
public class StartInstanceMessage extends ExecutorMessage {
    InstanceSpec spec;

    public StartInstanceMessage(MessageHeader header, InstanceSpec spec) {
        super(ExecutorMessageType.START_INSTANCE, header);
        this.spec = spec;
    }

    @Override
    public <T> T accept(ExecutorMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
