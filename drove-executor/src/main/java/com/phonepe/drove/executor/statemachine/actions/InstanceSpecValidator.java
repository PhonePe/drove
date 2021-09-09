package com.phonepe.drove.executor.statemachine.actions;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.statemachine.InstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;

/**
 *
 */
public class InstanceSpecValidator extends InstanceAction {
    @Override
    protected StateData<InstanceState, InstanceInfo> executeImpl(
            InstanceActionContext context, StateData<InstanceState, InstanceInfo> currentState) {
        return StateData.from(currentState, InstanceState.PROVISIONING);
    }

    @Override
    public void stop() {
        //Ignore this
    }
}
