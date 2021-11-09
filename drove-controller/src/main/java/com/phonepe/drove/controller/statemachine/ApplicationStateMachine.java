package com.phonepe.drove.controller.statemachine;

import com.phonepe.drove.common.ActionFactory;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.common.StateMachine;
import com.phonepe.drove.common.Transition;
import com.phonepe.drove.controller.statemachine.actions.AppOperationRouterAction;
import com.phonepe.drove.controller.statemachine.actions.CreateAppAction;
import com.phonepe.drove.controller.statemachine.actions.StartAppAction;
import com.phonepe.drove.controller.statemachine.actions.StopAppAction;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import lombok.NonNull;

import java.util.List;

/**
 *
 */
public class ApplicationStateMachine extends StateMachine<ApplicationInfo, ApplicationUpdateData, ApplicationState, AppActionContext, AppAction> {
    private static final List<Transition<ApplicationInfo, ApplicationUpdateData, ApplicationState, AppActionContext, AppAction>> TRANSITIONS
            = List.of(
            new Transition<>(ApplicationState.INIT, CreateAppAction.class, ApplicationState.MONITORING),
            new Transition<>(ApplicationState.MONITORING,
                             AppOperationRouterAction.class,
                             ApplicationState.DEPLOYMENT_REQUESTED,
                             ApplicationState.DESTROY_REQUESTED,
                             ApplicationState.SCALING_REQUESTED,
                             ApplicationState.RESTART_REQUESTED,
                             ApplicationState.SUSPEND_REQUESTED,
                             ApplicationState.PARTIAL_OUTAGE),
            new Transition<>(ApplicationState.DEPLOYMENT_REQUESTED,
                             StartAppAction.class,
                             ApplicationState.RUNNING,
                             ApplicationState.MONITORING),
            new Transition<>(ApplicationState.RUNNING,
                             AppOperationRouterAction.class,
                             ApplicationState.DEPLOYMENT_REQUESTED,
                             ApplicationState.DESTROY_REQUESTED,
                             ApplicationState.SCALING_REQUESTED,
                             ApplicationState.RESTART_REQUESTED,
                             ApplicationState.SUSPEND_REQUESTED,
                             ApplicationState.PARTIAL_OUTAGE),
            new Transition<>(ApplicationState.SUSPEND_REQUESTED, StopAppAction.class, ApplicationState.MONITORING),
            new Transition<>(ApplicationState.PARTIAL_OUTAGE,
                             StartAppAction.class,
                             ApplicationState.RUNNING,
                             ApplicationState.MONITORING));

    public ApplicationStateMachine(
            @NonNull StateData<ApplicationState, ApplicationInfo> initalState,
            AppActionContext context,
            ActionFactory<ApplicationInfo, ApplicationUpdateData, ApplicationState, AppActionContext, AppAction> actionFactory) {
        super(initalState, context, actionFactory, TRANSITIONS);
    }

}
