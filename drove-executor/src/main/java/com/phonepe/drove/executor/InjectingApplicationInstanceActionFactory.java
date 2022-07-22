package com.phonepe.drove.executor;

import com.google.inject.Injector;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorApplicationInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.application.ApplicationInstanceActionFactory;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.statemachine.Transition;

/**
 *
 */
public class InjectingApplicationInstanceActionFactory extends ApplicationInstanceActionFactory {
    private final Injector injector;

    public InjectingApplicationInstanceActionFactory(Injector injector) {
        this.injector = injector;
    }

    @Override
    public InstanceActionBase<ExecutorApplicationInstanceInfo, InstanceState, ApplicationInstanceSpec> create(Transition<ExecutorApplicationInstanceInfo, Void, InstanceState, InstanceActionContext<ApplicationInstanceSpec>, InstanceActionBase<ExecutorApplicationInstanceInfo, InstanceState, ApplicationInstanceSpec>> transition) {
        return injector.getInstance(transition.getAction());

    }
}
