package com.phonepe.drove.executor.statemachine.task.actions;

import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.ExecutorOptions;
import com.phonepe.drove.executor.model.ExecutorTaskInfo;
import com.phonepe.drove.executor.statemachine.common.actions.CommonExecutableCleanupAction;
import com.phonepe.drove.models.taskinstance.TaskState;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

/**
 *
 */
@Slf4j
public class TaskExecutableCleanupAction extends CommonExecutableCleanupAction<ExecutorTaskInfo, TaskState, TaskInstanceSpec> {

    @Inject
    public TaskExecutableCleanupAction(ExecutorOptions options) {
       super(options);
    }

    @Override
    protected TaskState defaultErrorState() {
        return TaskState.STOPPED;
    }

    @Override
    protected TaskState stoppedState() {
        return TaskState.STOPPED;
    }

}
