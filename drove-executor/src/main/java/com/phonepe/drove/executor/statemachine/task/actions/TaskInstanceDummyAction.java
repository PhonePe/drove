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
public abstract class TaskInstanceDummyAction extends TaskInstanceAction {
    private final TaskInstanceState state;

    protected TaskInstanceDummyAction(TaskInstanceState state) {
        this.state = state;
    }


    @Override
    protected StateData<TaskInstanceState, ExecutorTaskInstanceInfo> executeImpl(
            InstanceActionContext<TaskInstanceSpec> context,
            StateData<TaskInstanceState, ExecutorTaskInstanceInfo> currentState) {
        return StateData.from(currentState, state);
    }

    @Override
    public void stop() {
        //Nothing to be done here
    }

}
