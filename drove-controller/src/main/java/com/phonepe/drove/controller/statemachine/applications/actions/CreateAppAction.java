package com.phonepe.drove.controller.statemachine.applications.actions;

import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statemachine.applications.AppActionContext;
import com.phonepe.drove.controller.statemachine.applications.OperationDrivenAppAction;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import io.appform.functionmetrics.MonitoredFunction;
import com.phonepe.drove.statemachine.StateData;
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
    @MonitoredFunction
    public StateData<ApplicationState, ApplicationInfo> commandReceived(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation) {
        applicationStateDB.updateApplicationState(context.getAppId(), currentState.getData());
        val expectedInstances = applicationStateDB.application(context.getAppId())
                .map(ApplicationInfo::getInstances)
                .orElse(0L);
        if (expectedInstances > 0) {
            log.info("{} instances are expected for this app.", expectedInstances);
            return StateData.from(currentState, ApplicationState.RUNNING);
        }
        return StateData.from(currentState, ApplicationState.MONITORING);
    }
}