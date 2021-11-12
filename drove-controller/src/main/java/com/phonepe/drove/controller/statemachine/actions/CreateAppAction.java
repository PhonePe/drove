package com.phonepe.drove.controller.statemachine.actions;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statemachine.AppActionContext;
import com.phonepe.drove.controller.statemachine.OperationDrivenAppAction;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;

/**
 *
 */
@Slf4j
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
        applicationStateDB.updateApplicationState(context.getAppId(), currentState.getData());
        val runningInstances = applicationStateDB.instanceCount(context.getAppId(), InstanceState.HEALTHY);
        if(runningInstances > 0) {
            log.info("looks like {} instances are already running for this app.", runningInstances);
            applicationStateDB.updateInstanceCount(context.getAppId(), runningInstances);
            return StateData.from(currentState, ApplicationState.RUNNING);
        }
        return StateData.from(currentState, ApplicationState.MONITORING);
    }
}
