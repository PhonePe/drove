package com.phonepe.drove.executor.statemachine.task.actions;

import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorTaskInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.common.actions.CommonContainerCleanupAction;
import com.phonepe.drove.models.taskinstance.TaskState;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Slf4j
public class TaskDestroyAction extends CommonContainerCleanupAction<ExecutorTaskInfo, TaskState, TaskInstanceSpec> {

    @Override
    protected StateData<TaskState, ExecutorTaskInfo> preRemoveAction(
            InstanceActionContext<TaskInstanceSpec> context,
            StateData<TaskState, ExecutorTaskInfo> currentState) {
        return StateData.from(currentState, TaskState.DEPROVISIONING);
    }

    @Override
    protected TaskState defaultErrorState() {
        return TaskState.DEPROVISIONING;
    }

    @Override
    protected TaskState stoppedState() {
        return defaultErrorState();
    }

    @Override
    public void stop() {
        //Nothing to do here. This job is not stoppable
    }


}
