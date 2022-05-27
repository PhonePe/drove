package com.phonepe.drove.controller.statemachine;

import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import io.appform.simplefsm.Action;

/**
 *
 */
public abstract class AppAction implements Action<ApplicationInfo, ApplicationState, AppActionContext, ApplicationOperation> {
    @Override
    public void stop() {
        //Nothing to do here
    }

    public boolean cancel(final AppActionContext context) {
        return false;
    }
}
