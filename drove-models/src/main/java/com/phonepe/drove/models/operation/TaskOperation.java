package com.phonepe.drove.models.operation;

/**
 *
 */
public abstract class TaskOperation {
    private final TaskOperationType type;

    protected TaskOperation(TaskOperationType type) {
        this.type = type;
    }

    public abstract <T> T accept(final TaskOperationVisitor<T> visitor);
}
