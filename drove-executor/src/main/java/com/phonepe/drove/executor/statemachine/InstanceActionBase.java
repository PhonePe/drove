package com.phonepe.drove.executor.statemachine;


import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.DeploymentUnitSpec;
import com.phonepe.drove.executor.model.DeployedExecutorInstanceInfo;
import com.phonepe.drove.statemachine.Action;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 *
 */
@Slf4j
public abstract class InstanceActionBase<E extends DeployedExecutorInstanceInfo, S extends Enum<S>, T extends DeploymentUnitSpec> implements Action<E, S, InstanceActionContext<T>, Void> {

    @Override
    public final StateData<S, E> execute(
            InstanceActionContext<T> context,
            StateData<S, E> currentState) {
        if (isStopAllowed() && context.getAlreadyStopped().get()) {
            return StateData.from(currentState, stoppedState());
        }
        try {
            return executeImpl(context, currentState);
        }
        catch (Exception e) {
            val instanceSpec = context.getInstanceSpec();
            log.error("Error running action implementation for " + CommonUtils.instanceId(instanceSpec), e);
            return StateData.errorFrom(currentState, defaultErrorState(), e.getMessage());
        }
    }

    protected abstract StateData<S, E> executeImpl(
            InstanceActionContext<T> context, StateData<S, E> currentState);

    protected boolean isStopAllowed() {
        return true;
    }

    protected abstract S defaultErrorState();

    protected abstract S stoppedState();
}
