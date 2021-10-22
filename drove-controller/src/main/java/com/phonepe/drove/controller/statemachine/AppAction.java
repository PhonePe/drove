package com.phonepe.drove.controller.statemachine;

import com.phonepe.drove.common.Action;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;

/**
 *
 */
public abstract class AppAction implements Action<ApplicationInfo, ApplicationState, AppActionContext> {
    @Override
    public StateData<ApplicationState, ApplicationInfo> execute(
            AppActionContext context, StateData<ApplicationState, ApplicationInfo> currentState) {
        return executeImpl(context, currentState);
    }

    protected abstract StateData<ApplicationState, ApplicationInfo> executeImpl(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState);
}
