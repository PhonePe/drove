package com.phonepe.drove.controller.statemachine.actions;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statemachine.AppActionContext;
import com.phonepe.drove.controller.statemachine.OperationDrivenAppAction;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ops.ApplicationDeployOperation;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;

import static com.phonepe.drove.controller.utils.ControllerUtils.safeCast;

/**
 *
 */
@Slf4j
public class StartAppAction extends OperationDrivenAppAction {

    private final ApplicationStateDB applicationStateDB;

    @Inject
    public StartAppAction(
            ApplicationStateDB applicationStateDB) {
        this.applicationStateDB = applicationStateDB;
    }


    @Override
    protected StateData<ApplicationState, ApplicationInfo> commandReceived(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation) {
        val deploy = safeCast(operation, ApplicationDeployOperation.class);
        val requiredInstances = applicationStateDB.instanceCount(deploy.getAppId()) + deploy.getInstances();
        applicationStateDB.updateInstanceCount(deploy.getAppId(), requiredInstances);
        return StateData.from(currentState, ApplicationState.SCALING_REQUESTED);
    }

    @Override
    public void stop() {
        log.warn("Stop is not implemented for {}", getClass().getSimpleName());
    }
}
