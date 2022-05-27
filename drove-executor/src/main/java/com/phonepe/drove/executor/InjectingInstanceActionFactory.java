package com.phonepe.drove.executor;

import com.google.inject.Injector;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.InstanceState;
import io.appform.simplefsm.Transition;

/**
 *
 */
public class InjectingInstanceActionFactory implements InstanceActionFactory {
    private final Injector injector;

    public InjectingInstanceActionFactory(Injector injector) {
        this.injector = injector;
    }

    @Override
    public InstanceAction create(Transition<ExecutorInstanceInfo, Void, InstanceState, InstanceActionContext, InstanceAction> transition) {
        return injector.getInstance(transition.getAction());
    }
}
