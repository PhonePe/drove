package com.phonepe.drove.executor.statemachine.task;


import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorTaskInfo;
import com.phonepe.drove.executor.statemachine.ExecutorActionBase;
import com.phonepe.drove.models.taskinstance.TaskState;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Slf4j
public abstract class TaskAction extends ExecutorActionBase<ExecutorTaskInfo, TaskState, TaskInstanceSpec> {

    @Override
    protected TaskState stoppedState() {
        return TaskState.STOPPED;
    }
}
