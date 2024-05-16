package com.phonepe.drove.executor.statemachine.application.actions;

import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.common.actions.CommonContainerCleanupAction;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.statemachine.StateData;

/**
 *
 */
public class ApplicationInstanceDestroyAction
        extends CommonContainerCleanupAction<ExecutorInstanceInfo, InstanceState, ApplicationInstanceSpec> {

    public ApplicationInstanceDestroyAction() {
        //Nothing to do here
    }

    @Override
    protected InstanceState defaultErrorState() {
        return InstanceState.DEPROVISIONING;
    }

    @Override
    protected InstanceState stoppedState() {
        return defaultErrorState();
    }

    @Override
    protected StateData<InstanceState, ExecutorInstanceInfo> preRemoveAction(
            InstanceActionContext<ApplicationInstanceSpec> context,
            StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        return StateData.from(currentState, InstanceState.DEPROVISIONING);
    }
}
