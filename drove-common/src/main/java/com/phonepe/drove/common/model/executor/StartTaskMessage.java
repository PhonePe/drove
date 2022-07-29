package com.phonepe.drove.common.model.executor;

import com.phonepe.drove.common.model.ExecutorMessageType;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class StartTaskMessage extends ExecutorMessage {
    TaskInstanceSpec spec;

    public StartTaskMessage(
            MessageHeader header,
            ExecutorAddress address,
            TaskInstanceSpec spec) {
        super(ExecutorMessageType.START_TASK, header, address);
        this.spec = spec;
    }

    @Override
    public <T> T accept(ExecutorMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
