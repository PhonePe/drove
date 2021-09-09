package com.phonepe.drove.executor.statemachine;

import com.phonepe.drove.common.Action;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;

/**
 *
 */
public abstract class InstanceAction implements Action<InstanceInfo, InstanceState, InstanceActionContext> {

    @Override
    public final StateData<InstanceState, InstanceInfo> execute(
            InstanceActionContext context, StateData<InstanceState, InstanceInfo> currentState) {
        if(isStopAllowed()) {
            if (context.getAlreadyStopped().get()) {
                return StateData.from(currentState, InstanceState.STOPPING);
            }
        }
        return executeImpl(context, currentState);
    }

    protected abstract StateData<InstanceState, InstanceInfo> executeImpl(
            InstanceActionContext context, StateData<InstanceState, InstanceInfo> currentState);

    protected boolean isStopAllowed() {
        return true;
    }
}
