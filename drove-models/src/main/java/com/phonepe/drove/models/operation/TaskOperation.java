package com.phonepe.drove.models.operation;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.phonepe.drove.models.operation.taskops.TaskCreateOperation;
import com.phonepe.drove.models.operation.taskops.TaskKillOperation;
import lombok.Data;

/**
 *
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "CREATE", value = TaskCreateOperation.class),
        @JsonSubTypes.Type(name = "KILL", value = TaskKillOperation.class)
})
@Data
public abstract class TaskOperation {
    private final TaskOperationType type;

    protected TaskOperation(TaskOperationType type) {
        this.type = type;
    }

    public abstract <T> T accept(final TaskOperationVisitor<T> visitor);
}
