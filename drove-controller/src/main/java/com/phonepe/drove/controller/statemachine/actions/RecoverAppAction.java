package com.phonepe.drove.controller.statemachine.actions;

import com.phonepe.drove.controller.statemachine.AppActionContext;
import com.phonepe.drove.controller.statemachine.OperationDrivenAppAction;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import io.appform.functionmetrics.MonitoredFunction;
import io.appform.simplefsm.StateData;
import lombok.extern.slf4j.Slf4j;

/**
 * This state is there so that notifications wrt outage become easy. Do not delete this.
 */
@Slf4j
public class RecoverAppAction extends OperationDrivenAppAction {

    @Override
    @MonitoredFunction
    protected StateData<ApplicationState, ApplicationInfo> commandReceived(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation) {
        return StateData.from(currentState, ApplicationState.SCALING_REQUESTED);
    }
}
