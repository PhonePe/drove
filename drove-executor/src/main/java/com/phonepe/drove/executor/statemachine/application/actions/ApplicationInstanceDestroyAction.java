package com.phonepe.drove.executor.statemachine.application.actions;

import com.phonepe.drove.models.instance.InstanceState;

/**
 *
 */
public class ApplicationInstanceDestroyAction extends InstanceDummyAction {
    public ApplicationInstanceDestroyAction() {
        super(InstanceState.DEPROVISIONING);
    }

    @Override
    protected InstanceState defaultErrorState() {
        return InstanceState.DEPROVISIONING;
    }
}
