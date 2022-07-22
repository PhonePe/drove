package com.phonepe.drove.models.operation.taskops;

import com.phonepe.drove.models.operation.TaskOperation;
import com.phonepe.drove.models.operation.TaskOperationType;
import com.phonepe.drove.models.operation.TaskOperationVisitor;
import com.phonepe.drove.models.task.TaskSpec;
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
public class TaskCreateOperation extends TaskOperation {

    TaskSpec spec;

    public TaskCreateOperation(TaskSpec spec) {
        super(TaskOperationType.CREATE);
        this.spec = spec;
    }

    @Override
    public <T> T accept(final TaskOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
