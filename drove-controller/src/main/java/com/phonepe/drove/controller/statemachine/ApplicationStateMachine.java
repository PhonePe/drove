package com.phonepe.drove.controller.statemachine;

import com.phonepe.drove.common.ActionFactory;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.common.StateMachine;
import com.phonepe.drove.common.Transition;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import lombok.NonNull;

import java.util.List;

/**
 *
 */
public class ApplicationStateMachine extends StateMachine<ApplicationInfo, ApplicationState, AppActionContext, AppAction> {
    protected ApplicationStateMachine(
            @NonNull StateData<ApplicationState, ApplicationInfo> initalState,
            AppActionContext context,
            ActionFactory<ApplicationInfo, ApplicationState, AppActionContext, AppAction> actionFactory,
            List<Transition<ApplicationInfo, ApplicationState, AppActionContext, AppAction>> transitions) {
        super(initalState, context, actionFactory, transitions);
    }
}
