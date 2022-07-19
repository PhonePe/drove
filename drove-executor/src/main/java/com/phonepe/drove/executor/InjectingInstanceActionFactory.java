package com.phonepe.drove.executor;

import com.google.inject.Injector;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.application.ApplicationInstanceActionFactory;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.statemachine.Transition;

/**
 *
 */
public class InjectingInstanceActionFactory extends ApplicationInstanceActionFactory {
    private final Injector injector;

    public InjectingInstanceActionFactory(Injector injector) {
        this.injector = injector;
    }

    @Override
    public InstanceActionBase<ExecutorInstanceInfo, InstanceState, ApplicationInstanceSpec> create(Transition<ExecutorInstanceInfo, Void, InstanceState, InstanceActionContext<ApplicationInstanceSpec>, InstanceActionBase<ExecutorInstanceInfo, InstanceState, ApplicationInstanceSpec>> transition) {
        return injector.getInstance(transition.getAction());

    }
}
