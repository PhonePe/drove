package com.phonepe.drove.executor.statemachine.actions;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.statemachine.InstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;

import javax.inject.Inject;

/**
 *
 */
public class InstanceDummyAction extends InstanceAction {
    private final InstanceState state;

    @Inject
    public InstanceDummyAction(InstanceState state) {
        this.state = state;
    }

    @Override
    public StateData<InstanceState, InstanceInfo> execute(
            InstanceActionContext context, StateData<InstanceState, InstanceInfo> currentState) {
        return StateData.from(currentState, state);
    }

    @Override
    public void stop() {
        //Nothing to be done here
    }
}
