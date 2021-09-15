package com.phonepe.drove.controller.statemachine;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;

/**
 *
 */
public class DeployAppAction extends AppAction {
    @Override
    public StateData<ApplicationState, ApplicationInfo> execute(
            AppActionContext context, StateData<ApplicationState, ApplicationInfo> currentState) {
        return null;
    }

    @Override
    public void stop() {

    }
}
