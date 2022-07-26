package com.phonepe.drove.executor;

import com.google.inject.Injector;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.ExecutorActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.application.ApplicationInstanceActionFactory;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.statemachine.Transition;

/**
 *
 */
public class InjectingApplicationInstanceActionFactory implements ApplicationInstanceActionFactory {
    private final Injector injector;

    public InjectingApplicationInstanceActionFactory(Injector injector) {
        this.injector = injector;
    }

    @Override
    public ExecutorActionBase<ExecutorInstanceInfo, InstanceState, ApplicationInstanceSpec> create(Transition<ExecutorInstanceInfo, Void, InstanceState, InstanceActionContext<ApplicationInstanceSpec>, ExecutorActionBase<ExecutorInstanceInfo, InstanceState, ApplicationInstanceSpec>> transition) {
        return injector.getInstance(transition.getAction());

    }
}
