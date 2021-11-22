package com.phonepe.drove.controller.statemachine.actions;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.controller.statemachine.AppActionContext;
import com.phonepe.drove.controller.statemachine.OperationDrivenAppAction;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;

/**
 *
 */
public class DestroyAppAction extends OperationDrivenAppAction {

    public DestroyAppAction() {
    }

    @Override
    public void stop() {

    }

    @Override
    protected StateData<ApplicationState, ApplicationInfo> commandReceived(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation) {
        return StateData.from(currentState, ApplicationState.DESTROYED);
    }
}
