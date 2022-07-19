package com.phonepe.drove.models.operation.taskops;

import com.phonepe.drove.models.operation.TaskOperation;
import com.phonepe.drove.models.operation.TaskOperationType;
import com.phonepe.drove.models.operation.TaskOperationVisitor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class TaskKillOperation extends TaskOperation {

    String taskId;

    public TaskKillOperation(String taskId) {
        super(TaskOperationType.KILL);
        this.taskId = taskId;
    }

    @Override
    public <T> T accept(TaskOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
