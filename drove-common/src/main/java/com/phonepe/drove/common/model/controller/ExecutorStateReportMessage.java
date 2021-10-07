package com.phonepe.drove.common.model.controller;

import com.phonepe.drove.common.model.ControllerMessageType;
import com.phonepe.drove.common.model.ExecutorResourceSnapshot;
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
public class ExecutorStateReportMessage extends ControllerMessage {

    ExecutorResourceSnapshot executorState;

    public ExecutorStateReportMessage(
            MessageHeader header,
            ExecutorResourceSnapshot executorState) {
        super(ControllerMessageType.EXECUTOR_STATE_REPORT, header);
        this.executorState = executorState;
    }

    @Override
    public <T> T accept(ControllerMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
