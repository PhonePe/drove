package com.phonepe.drove.controller.statemachine.actions;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statemachine.AppActionContext;
import com.phonepe.drove.controller.statemachine.OperationDrivenAppAction;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;

import javax.inject.Inject;

/**
 *
 */
public class CreateAppAction extends OperationDrivenAppAction {
    private final ApplicationStateDB applicationStateDB;

    @Inject
    public CreateAppAction(ApplicationStateDB applicationStateDB) {
        this.applicationStateDB = applicationStateDB;
    }

    @Override
    public void stop() {
        //This is not stoppable
    }

    @Override
    public StateData<ApplicationState, ApplicationInfo> commandReceived(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation) {
        applicationStateDB.updateApplicationState(ControllerUtils.appId(context.getApplicationSpec()), currentState.getData());
        return StateData.from(currentState, ApplicationState.MONITORING);
    }
}
