package com.phonepe.drove.executor.statemachine;

import com.phonepe.drove.common.Action;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;

/**
 *
 */
public abstract class InstanceAction extends Action<ExecutorInstanceInfo, InstanceState, InstanceActionContext, Void> {

    @Override
    public final StateData<InstanceState, ExecutorInstanceInfo> execute(
            InstanceActionContext context,
            StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        if (isStopAllowed() && context.getAlreadyStopped().get()) {
            return StateData.from(currentState, InstanceState.STOPPING);
        }
        return executeImpl(context, currentState);
    }

    protected abstract StateData<InstanceState, ExecutorInstanceInfo> executeImpl(
            InstanceActionContext context, StateData<InstanceState, ExecutorInstanceInfo> currentState);

    protected boolean isStopAllowed() {
        return true;
    }
}
