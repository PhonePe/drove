package com.phonepe.drove.controller.statemachine.actions;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statemachine.AppActionContext;
import com.phonepe.drove.controller.statemachine.ApplicationUpdateData;
import com.phonepe.drove.controller.statemachine.OperationDrivenAppAction;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ops.ApplicationScaleOperation;
import com.phonepe.drove.models.operation.ops.ApplicationSuspendOperation;
import lombok.val;

import javax.inject.Inject;

import static com.phonepe.drove.controller.utils.ControllerUtils.safeCast;

/**
 *
 */
public class StopAppAction extends OperationDrivenAppAction {
    private final ApplicationStateDB applicationStateDB;

    @Inject
    public StopAppAction(ApplicationStateDB applicationStateDB) {
        this.applicationStateDB = applicationStateDB;
    }

    @Override
    public void stop() {

    }

    @Override
    protected StateData<ApplicationState, ApplicationInfo> commandReceived(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation) {
        val stopOp = safeCast(operation, ApplicationSuspendOperation.class);
        applicationStateDB.updateInstanceCount(context.getAppId(), 0);
        context.recordUpdate(
                new ApplicationUpdateData(
                        new ApplicationScaleOperation(context.getAppId(), 0, stopOp.getOpSpec()),
                        null));
        return StateData.from(currentState, ApplicationState.SCALING_REQUESTED);
    }
}
