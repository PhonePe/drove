package com.phonepe.drove.models.operation.taskops;

import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.TaskOperation;
import com.phonepe.drove.models.operation.TaskOperationType;
import com.phonepe.drove.models.operation.TaskOperationVisitor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class TaskKillOperation extends TaskOperation {

    @NotEmpty
    String sourceAppName;

    @NotEmpty
    String taskId;

    @NotNull
    @Valid
    ClusterOpSpec opSpec;

    public TaskKillOperation(String sourceAppName, String taskId, ClusterOpSpec opSpec) {
        super(TaskOperationType.KILL);
        this.sourceAppName = sourceAppName;
        this.taskId = taskId;
        this.opSpec = opSpec;
    }

    @Override
    public <T> T accept(TaskOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
