package com.phonepe.drove.controller.engine;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.phonepe.drove.controller.statemachine.AppAction;
import com.phonepe.drove.controller.statemachine.AppActionContext;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import io.appform.simplefsm.ActionFactory;
import io.appform.simplefsm.Transition;

/**
 *
 */
@Singleton
public class InjectingAppActionFactory implements ActionFactory<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext, AppAction> {
    private final Injector injector;

    @Inject
    public InjectingAppActionFactory(Injector injector) {
        this.injector = injector;
    }

    @Override
    public AppAction create(Transition<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext, AppAction> transition) {
        return injector.getInstance(transition.getAction());
    }
}
