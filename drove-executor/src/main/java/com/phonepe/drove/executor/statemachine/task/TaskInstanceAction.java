package com.phonepe.drove.executor.statemachine.task;


import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorTaskInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionBase;
import com.phonepe.drove.models.taskinstance.TaskInstanceState;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Slf4j
public abstract class TaskInstanceAction extends InstanceActionBase<ExecutorTaskInstanceInfo, TaskInstanceState, TaskInstanceSpec> {

    @Override
    protected TaskInstanceState stoppedState() {
        return TaskInstanceState.STOPPED;
    }
}
