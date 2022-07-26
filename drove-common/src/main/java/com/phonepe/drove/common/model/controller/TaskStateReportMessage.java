package com.phonepe.drove.common.model.controller;

import com.phonepe.drove.common.model.ControllerMessageType;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TaskStateReportMessage extends ControllerMessage {
    ExecutorResourceSnapshot resourceSnapshot;
    TaskInfo instanceInfo;

    public TaskStateReportMessage(
            MessageHeader header,
            ExecutorResourceSnapshot resourceSnapshot,
            TaskInfo instanceInfo) {
        super(ControllerMessageType.TASK_STATE_REPORT, header);
        this.resourceSnapshot = resourceSnapshot;
        this.instanceInfo = instanceInfo;
    }

    @Override
    public <T> T accept(ControllerMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
