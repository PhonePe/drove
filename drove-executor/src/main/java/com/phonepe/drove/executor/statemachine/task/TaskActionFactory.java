package com.phonepe.drove.executor.statemachine.task;

import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.ExecutorActionFactory;
import com.phonepe.drove.executor.model.ExecutorTaskInfo;
import com.phonepe.drove.models.taskinstance.TaskState;

/**
 *
 */
public interface TaskActionFactory extends ExecutorActionFactory<ExecutorTaskInfo, TaskState, TaskInstanceSpec> {

}
