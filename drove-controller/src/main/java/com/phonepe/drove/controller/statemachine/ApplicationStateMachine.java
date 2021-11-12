package com.phonepe.drove.controller.statemachine;

import com.phonepe.drove.common.ActionFactory;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.common.StateMachine;
import com.phonepe.drove.common.Transition;
import com.phonepe.drove.controller.statemachine.actions.*;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import lombok.NonNull;

import java.util.List;

/**
 *
 */
public class ApplicationStateMachine extends StateMachine<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext, AppAction> {
    private static final List<Transition<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext, AppAction>> TRANSITIONS
            = List.of(
            new Transition<>(ApplicationState.INIT, CreateAppAction.class, ApplicationState.MONITORING, ApplicationState.RUNNING),
            new Transition<>(ApplicationState.MONITORING,
                             AppOperationRouterAction.class,
                             ApplicationState.DEPLOYMENT_REQUESTED,
                             ApplicationState.DESTROY_REQUESTED,
                             ApplicationState.SCALING_REQUESTED,
                             ApplicationState.RESTART_REQUESTED,
                             ApplicationState.SUSPEND_REQUESTED,
                             ApplicationState.OUTAGE_DETECTED,
                             ApplicationState.MONITORING,
                             ApplicationState.RUNNING),
            new Transition<>(ApplicationState.DEPLOYMENT_REQUESTED,
                             StartAppAction.class,
                             ApplicationState.SCALING_REQUESTED),
            new Transition<>(ApplicationState.RUNNING,
                             AppOperationRouterAction.class,
                             ApplicationState.DEPLOYMENT_REQUESTED,
                             ApplicationState.DESTROY_REQUESTED,
                             ApplicationState.SCALING_REQUESTED,
                             ApplicationState.RESTART_REQUESTED,
                             ApplicationState.SUSPEND_REQUESTED,
                             ApplicationState.OUTAGE_DETECTED,
                             ApplicationState.MONITORING,
                             ApplicationState.RUNNING),
            new Transition<>(ApplicationState.SUSPEND_REQUESTED,
                             StopAppAction.class,
                             ApplicationState.SCALING_REQUESTED),
            new Transition<>(ApplicationState.OUTAGE_DETECTED,
                             RecoverAppAction.class,
                             ApplicationState.SCALING_REQUESTED),
            new Transition<>(ApplicationState.SCALING_REQUESTED,
                             ScaleAppAction.class,
                             ApplicationState.SCALING_REQUESTED,
                             ApplicationState.RUNNING,
                             ApplicationState.MONITORING),
            new Transition<>(ApplicationState.RESTART_REQUESTED,
                             RestartAppAction.class,
                             ApplicationState.RUNNING,
                             ApplicationState.MONITORING));

    public ApplicationStateMachine(
            @NonNull StateData<ApplicationState, ApplicationInfo> initalState,
            AppActionContext context,
            ActionFactory<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext, AppAction> actionFactory) {
        super(initalState, context, actionFactory, TRANSITIONS);
    }

}
