package com.phonepe.drove.executor.statemachine.application.actions;

import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorApplicationInstanceInfo;
import com.phonepe.drove.executor.statemachine.application.ApplicationInstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.InstanceState;
import io.appform.functionmetrics.MonitoredFunction;
import com.phonepe.drove.statemachine.StateData;

/**
 *
 */
public abstract class InstanceDummyAction extends ApplicationInstanceAction {
    private final InstanceState state;

    protected InstanceDummyAction(InstanceState state) {
        this.state = state;
    }

    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<InstanceState, ExecutorApplicationInstanceInfo> executeImpl(
            InstanceActionContext<ApplicationInstanceSpec> context, StateData<InstanceState, ExecutorApplicationInstanceInfo> currentState) {
        return StateData.from(currentState, state);
    }

    @Override
    public void stop() {
        //Nothing to be done here
    }
}
