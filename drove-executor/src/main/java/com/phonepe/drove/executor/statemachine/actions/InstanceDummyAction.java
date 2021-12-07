package com.phonepe.drove.executor.statemachine.actions;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.InstanceState;
import io.appform.functionmetrics.MonitoredFunction;

/**
 *
 */
public abstract class InstanceDummyAction extends InstanceAction {
    private final InstanceState state;

    protected InstanceDummyAction(InstanceState state) {
        this.state = state;
    }

    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<InstanceState, ExecutorInstanceInfo> executeImpl(
            InstanceActionContext context, StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        return StateData.from(currentState, state);
    }

    @Override
    public void stop() {
        //Nothing to be done here
    }
}
