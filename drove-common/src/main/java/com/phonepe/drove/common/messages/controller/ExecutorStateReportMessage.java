package com.phonepe.drove.common.messages.controller;

import com.phonepe.drove.internalmodels.ControllerMessageType;
import com.phonepe.drove.internalmodels.ExecutorState;
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
public class ExecutorStateReportMessage extends ControllerMessage {

    ExecutorState executorState;

    public ExecutorStateReportMessage(
            MessageHeader header,
            ExecutorState executorState) {
        super(ControllerMessageType.EXECUTOR_STATE_REPORT, header);
        this.executorState = executorState;
    }

    @Override
    public <T> T accept(ControllerMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
