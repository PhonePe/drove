package com.phonepe.drove.controller.statemachine.applications;

import com.phonepe.drove.controller.statemachine.applications.actions.*;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.statemachine.ActionFactory;
import com.phonepe.drove.statemachine.StateData;
import com.phonepe.drove.statemachine.StateMachine;
import com.phonepe.drove.statemachine.Transition;
import lombok.NonNull;

import java.util.List;

import static com.phonepe.drove.models.application.ApplicationState.*;

/**
 *
 */
public class ApplicationStateMachine extends StateMachine<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext, AppAction> {
    private static final List<Transition<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext, AppAction>> TRANSITIONS;

    static {
        TRANSITIONS = List.of(
                new Transition<>(INIT,
                                 CreateAppAction.class,
                                 MONITORING,
                                 RUNNING),
                new Transition<>(MONITORING,
                                 AppOperationRouterAction.class,
                                 OUTAGE_DETECTED,
                                 DESTROY_REQUESTED,
                                 SCALING_REQUESTED,
                                 MONITORING),
                new Transition<>(RUNNING,
                                 AppOperationRouterAction.class,
                                 STOP_INSTANCES_REQUESTED,
                                 SCALING_REQUESTED,
                                 REPLACE_INSTANCES_REQUESTED,
                                 OUTAGE_DETECTED,
                                 RUNNING),
                new Transition<>(OUTAGE_DETECTED,
                                 RecoverAppAction.class,
                                 SCALING_REQUESTED),
                new Transition<>(SCALING_REQUESTED,
                                 ScaleAppAction.class,
                                 SCALING_REQUESTED,
                                 RUNNING,
                                 MONITORING),
                new Transition<>(REPLACE_INSTANCES_REQUESTED,
                                 ReplaceInstancesAppAction.class,
                                 RUNNING,
                                 MONITORING),
                new Transition<>(STOP_INSTANCES_REQUESTED,
                                 StopAppInstancesAction.class,
                                 RUNNING,
                                 MONITORING),
                new Transition<>(DESTROY_REQUESTED,
                                 DestroyAppAction.class,
                                 DESTROYED));
    }

    public ApplicationStateMachine(
            @NonNull StateData<ApplicationState, ApplicationInfo> initalState,
            AppActionContext context,
            ActionFactory<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext, AppAction> actionFactory) {
        super(initalState, context, actionFactory, TRANSITIONS);
    }

}
