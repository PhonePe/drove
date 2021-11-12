package com.phonepe.drove.controller.statemachine.actions;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.controller.statemachine.AppAction;
import com.phonepe.drove.controller.statemachine.AppActionContext;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ApplicationOperationVisitor;
import com.phonepe.drove.models.operation.ops.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Optional;

/**
 *
 */
@Slf4j
public class AppOperationRouterAction extends AppAction {

    @SneakyThrows
    @Override
    public StateData<ApplicationState, ApplicationInfo> execute(
            AppActionContext context, StateData<ApplicationState, ApplicationInfo> currentState) {
        return context.getUpdate()
                .map(operation -> {
                    log.info("Received command of type: {}", operation.getType());
                    val newState = moveToNextState(currentState, operation).orElse(null);
                    if (null != newState) {
                        log.info("App move to new state: {}", newState);
                        return newState;
                    }
                    log.info("Nothing to be routed. Going back to previous state");
                    return currentState;
                })
                .orElse(null);
    }

    @Override
    public void stop() {
        //TODO::IMPLEMENT THIS
    }

    protected Optional<StateData<ApplicationState, ApplicationInfo>> moveToNextState(
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation) {
        return operation.accept(new ApplicationOperationVisitor<>() {
            @Override
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationCreateOperation create) {
                return Optional.empty();
            }

            @Override
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationUpdateOperation update) {
                return Optional.empty(); //TODO
            }

            @Override
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationInfoOperation info) {
                return Optional.empty();
            }

            @Override
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationDestroyOperation destroy) {
                return Optional.of(StateData.from(currentState, ApplicationState.DESTROY_REQUESTED));
            }

            @Override
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationDeployOperation deploy) {
                return Optional.of(StateData.from(currentState, ApplicationState.DEPLOYMENT_REQUESTED));
            }

            @Override
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationStopInstancesOperation stopInstances) {
                return Optional.of(StateData.from(currentState, ApplicationState.STOP_INSTANCES_REQUESTED));
            }

            @Override
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationScaleOperation scale) {
                return Optional.of(StateData.from(currentState, ApplicationState.SCALING_REQUESTED));
            }

            @Override
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationRestartOperation restart) {
                return Optional.of(StateData.from(currentState, ApplicationState.RESTART_REQUESTED));
            }

            @Override
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationSuspendOperation suspend) {
                return Optional.of(StateData.from(currentState, ApplicationState.SUSPEND_REQUESTED));
            }

            @Override
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationRecoverOperation recover) {
                return Optional.of(StateData.from(currentState, ApplicationState.OUTAGE_DETECTED));
            }
        });
    }

}
