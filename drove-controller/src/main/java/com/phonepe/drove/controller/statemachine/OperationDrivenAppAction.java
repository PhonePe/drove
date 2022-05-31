package com.phonepe.drove.controller.statemachine;

import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 *
 */
@Slf4j
public abstract class OperationDrivenAppAction extends AppAction {
    @Override
    public final StateData<ApplicationState, ApplicationInfo> execute(
            AppActionContext context, StateData<ApplicationState, ApplicationInfo> currentState) {
        val operation = context.getUpdate().orElse(null);
        if(null == operation) {
            log.warn("OperationDrivenAppAction triggered without any available operation. Returning to old state");
            return StateData.errorFrom(currentState, ApplicationState.RUNNING, "No operation available");
        }
        try {
            return commandReceived(context, currentState, operation);
        }
        catch (Exception e) {
            log.error("Error occurred: ", e);
            return StateData.errorFrom(currentState, ApplicationState.RUNNING, "Error: " + e.getMessage());
        }
        finally {
            log.debug("Acking operation of type: {}. Status: {}", operation.getType(), context.ackUpdate());
        }
    }

    protected abstract StateData<ApplicationState, ApplicationInfo> commandReceived(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation);
}
