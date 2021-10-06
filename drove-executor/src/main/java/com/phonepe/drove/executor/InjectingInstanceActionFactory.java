package com.phonepe.drove.executor;

import com.google.inject.Injector;
import com.phonepe.drove.common.Transition;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.InstanceState;

/**
 *
 */
public class InjectingInstanceActionFactory implements InstanceActionFactory {
    private final Injector injector;

    public InjectingInstanceActionFactory(Injector injector) {
        this.injector = injector;
    }

    @Override
    public InstanceAction create(Transition<ExecutorInstanceInfo, InstanceState, InstanceActionContext, InstanceAction> transition) {
        return injector.getInstance(transition.getAction());
    }
}
