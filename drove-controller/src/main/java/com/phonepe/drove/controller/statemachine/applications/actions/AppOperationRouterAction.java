/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.controller.statemachine.applications.actions;

import com.phonepe.drove.controller.statemachine.applications.AppAction;
import com.phonepe.drove.controller.statemachine.applications.AppActionContext;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ApplicationOperationVisitor;
import com.phonepe.drove.models.operation.ops.*;
import io.appform.functionmetrics.MonitoredFunction;
import com.phonepe.drove.statemachine.StateData;
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
    @MonitoredFunction
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
                    log.info("Nothing to be done. Going back to previous state. Operation of type {} is being ignored.",
                             operation.getType());
                    context.ackUpdate(); // In case we can't do anything, eat up the operation
                    return currentState;
                })
                .orElse(null);
    }

    protected Optional<StateData<ApplicationState, ApplicationInfo>> moveToNextState(
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation) {
        return operation.accept(new ApplicationOperationVisitor<>() {
            @Override
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationCreateOperation create) {
                return Optional.empty(); //This happens when someone sends create on running app
            }

            @Override
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationDestroyOperation destroy) {
                return Optional.of(StateData.from(currentState, ApplicationState.DESTROY_REQUESTED));
            }

            @Override
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationStartInstancesOperation deploy) {
                throw new IllegalStateException("DEPLOY operations should have been changed to scale operation");
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
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationReplaceInstancesOperation replaceInstances) {
                return Optional.of(StateData.from(currentState, ApplicationState.REPLACE_INSTANCES_REQUESTED));
            }

            @Override
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationSuspendOperation suspend) {
                throw new IllegalStateException("SUSPEND operations should have been changed to scale operation");
            }

            @Override
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationRecoverOperation recover) {
                return Optional.of(StateData.from(currentState, ApplicationState.OUTAGE_DETECTED));
            }
        });
    }

}
