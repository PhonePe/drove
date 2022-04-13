package com.phonepe.drove.executor.statemachine.actions;

import com.phonepe.drove.models.instance.InstanceState;

/**
 *
 */
public class InstanceDestroyAction extends InstanceDummyAction {
    public InstanceDestroyAction() {
        super(InstanceState.DEPROVISIONING);
    }

    @Override
    protected InstanceState defaultErrorState() {
        return InstanceState.DEPROVISIONING;
    }
}
