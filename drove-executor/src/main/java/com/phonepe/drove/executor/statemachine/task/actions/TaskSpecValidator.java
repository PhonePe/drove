package com.phonepe.drove.executor.statemachine.task.actions;

import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorTaskInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.task.TaskAction;
import com.phonepe.drove.models.taskinstance.TaskState;
import com.phonepe.drove.statemachine.StateData;

/**
 *
 */
public class TaskSpecValidator extends TaskAction {

    @Override
    protected StateData<TaskState, ExecutorTaskInfo> executeImpl(
            InstanceActionContext<TaskInstanceSpec> context,
            StateData<TaskState, ExecutorTaskInfo> currentState) {
        return StateData.from(currentState, TaskState.PROVISIONING);

    }

    @Override
    protected TaskState defaultErrorState() {
        return TaskState.STOPPING;
    }

    @Override
    public void stop() {
        //Ignore this
    }
}
