package com.phonepe.drove.controller.engine;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.phonepe.drove.common.ActionFactory;
import com.phonepe.drove.common.Transition;
import com.phonepe.drove.controller.statemachine.AppAction;
import com.phonepe.drove.controller.statemachine.AppActionContext;
import com.phonepe.drove.controller.statemachine.ApplicationUpdateData;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;

/**
 *
 */
@Singleton
public class InjectingAppActionFactory implements ActionFactory<ApplicationInfo, ApplicationUpdateData, ApplicationState, AppActionContext, AppAction> {
    private final Injector injector;

    @Inject
    public InjectingAppActionFactory(Injector injector) {
        this.injector = injector;
    }

    @Override
    public AppAction create(Transition<ApplicationInfo, ApplicationUpdateData, ApplicationState, AppActionContext, AppAction> transition) {
        return injector.getInstance(transition.getAction());
    }
}
