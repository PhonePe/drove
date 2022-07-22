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
public class ApplicationInstanceSpecValidator extends ApplicationInstanceAction {
    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<InstanceState, ExecutorApplicationInstanceInfo> executeImpl(
            InstanceActionContext<ApplicationInstanceSpec> context, StateData<InstanceState, ExecutorApplicationInstanceInfo> currentState) {
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
