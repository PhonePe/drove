package com.phonepe.drove.executor.statemachine.actions;

import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.InstanceState;
import io.appform.functionmetrics.MonitoredFunction;
import io.appform.simplefsm.StateData;

/**
 *
 */
public class InstanceSpecValidator extends InstanceAction {
    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<InstanceState, ExecutorInstanceInfo> executeImpl(
            InstanceActionContext context, StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        return StateData.from(currentState, InstanceState.PROVISIONING);
    }

    @Override
    protected InstanceState defaultErrorState() {
        return InstanceState.STOPPING;
    }

    @Override
    public void stop() {
        //Ignore this
    }
}
