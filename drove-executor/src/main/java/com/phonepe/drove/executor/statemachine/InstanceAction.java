package com.phonepe.drove.executor.statemachine;


import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import io.appform.simplefsm.Action;
import io.appform.simplefsm.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 *
 */
@Slf4j
public abstract class InstanceAction implements Action<ExecutorInstanceInfo, InstanceState, InstanceActionContext, Void> {

    @Override
    public final StateData<InstanceState, ExecutorInstanceInfo> execute(
            InstanceActionContext context,
            StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        if (isStopAllowed() && context.getAlreadyStopped().get()) {
            return StateData.from(currentState, InstanceState.STOPPING);
        }
        try {
            return executeImpl(context, currentState);
        }
        catch (Exception e) {
            val instanceSpec = context.getInstanceSpec();
            log.error("Error running action implementation for "
                              + instanceSpec.getAppId() + "/" + instanceSpec.getInstanceId(),
                      e);
            return StateData.errorFrom(currentState, defaultErrorState(), e.getMessage());
        }
    }

    protected abstract StateData<InstanceState, ExecutorInstanceInfo> executeImpl(
            InstanceActionContext context, StateData<InstanceState, ExecutorInstanceInfo> currentState);

    protected boolean isStopAllowed() {
        return true;
    }

    protected abstract InstanceState defaultErrorState();
}
