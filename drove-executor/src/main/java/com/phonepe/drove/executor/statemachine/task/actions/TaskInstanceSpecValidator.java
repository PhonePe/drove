package com.phonepe.drove.executor.statemachine.task.actions;

import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorTaskInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.task.TaskInstanceAction;
import com.phonepe.drove.models.taskinstance.TaskInstanceState;
import com.phonepe.drove.statemachine.StateData;

/**
 *
 */
public class TaskInstanceSpecValidator extends TaskInstanceAction {

    @Override
    protected StateData<TaskInstanceState, ExecutorTaskInstanceInfo> executeImpl(
            InstanceActionContext<TaskInstanceSpec> context,
            StateData<TaskInstanceState, ExecutorTaskInstanceInfo> currentState) {
        return StateData.from(currentState, TaskInstanceState.PROVISIONING);

    }

    @Override
    protected TaskInstanceState defaultErrorState() {
        return TaskInstanceState.STOPPING;
    }

    @Override
    public void stop() {
        //Ignore this
    }
}
