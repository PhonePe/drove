package com.phonepe.drove.models.operation;

import com.phonepe.drove.models.operation.taskops.TaskCreateOperation;
import com.phonepe.drove.models.operation.taskops.TaskKillOperation;

/**
 *
 */
public interface TaskOperationVisitor<T> {

    T visit(final TaskCreateOperation create);

    T visit(final TaskKillOperation kill);
}
