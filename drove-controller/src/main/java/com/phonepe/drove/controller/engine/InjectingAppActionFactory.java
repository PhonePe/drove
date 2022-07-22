package com.phonepe.drove.controller.engine;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.phonepe.drove.controller.statemachine.applications.AppAction;
import com.phonepe.drove.controller.statemachine.applications.AppActionContext;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.statemachine.ActionFactory;
import com.phonepe.drove.statemachine.Transition;

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
