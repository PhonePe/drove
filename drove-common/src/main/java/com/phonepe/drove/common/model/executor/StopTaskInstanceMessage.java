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
public class StopTaskInstanceMessage extends ExecutorMessage {

    String taskInstanceId;

    public StopTaskInstanceMessage(
            MessageHeader header,
            ExecutorAddress address,
            String taskInstanceId) {
        super(ExecutorMessageType.STOP_TASK_INSTANCE, header, address);
        this.taskInstanceId = taskInstanceId;
    }

    @Override
    public <T> T accept(ExecutorMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
