package com.phonepe.drove.executor.statemachine.task.actions;

import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.ExecutorOptions;
import com.phonepe.drove.executor.model.ExecutorTaskInstanceInfo;
import com.phonepe.drove.executor.statemachine.common.actions.CommonExecutableCleanupAction;
import com.phonepe.drove.models.taskinstance.TaskInstanceState;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

/**
 *
 */
@Slf4j
public class TaskExecutableCleanupAction extends CommonExecutableCleanupAction<ExecutorTaskInstanceInfo, TaskInstanceState, TaskInstanceSpec> {

    @Inject
    public TaskExecutableCleanupAction(ExecutorOptions options) {
       super(options);
    }

    @Override
    protected TaskInstanceState defaultErrorState() {
        return TaskInstanceState.STOPPED;
    }

    @Override
    protected TaskInstanceState stoppedState() {
        return TaskInstanceState.STOPPED;
    }

}
