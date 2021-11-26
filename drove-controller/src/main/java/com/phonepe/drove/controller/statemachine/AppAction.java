package com.phonepe.drove.controller.statemachine;

import com.phonepe.drove.common.Action;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;

/**
 *
 */
public abstract class AppAction extends Action<ApplicationInfo, ApplicationState, AppActionContext, com.phonepe.drove.models.operation.ApplicationOperation> {
    @Override
    public void stop() {
        //Nothing to do here
    }

    public boolean cancel(final AppActionContext context) {
        return false;
    }
}
