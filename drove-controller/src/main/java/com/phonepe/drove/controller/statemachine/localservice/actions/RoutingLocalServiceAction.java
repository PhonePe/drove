/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.controller.statemachine.localservice.actions;

import com.phonepe.drove.controller.statemachine.localservice.LocalServiceAction;
import com.phonepe.drove.controller.statemachine.localservice.LocalServiceActionContext;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.models.operation.LocalServiceOperation;
import com.phonepe.drove.models.operation.LocalServiceOperationVisitor;
import com.phonepe.drove.models.operation.localserviceops.*;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Optional;

/**
 *
 */
@Slf4j
public class RoutingLocalServiceAction extends LocalServiceAction {
    @Override
    public StateData<LocalServiceState, LocalServiceInfo> execute(
            LocalServiceActionContext context,
            StateData<LocalServiceState, LocalServiceInfo> currentState) {
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

    protected Optional<StateData<LocalServiceState, LocalServiceInfo>> moveToNextState(
            StateData<LocalServiceState, LocalServiceInfo> currentState,
            LocalServiceOperation operation) {
        return operation.accept(new LocalServiceOperationVisitor<>() {
            @Override
            public Optional<StateData<LocalServiceState, LocalServiceInfo>> visit(LocalServiceCreateOperation localServiceCreateOperation) {
                return Optional.empty();
            }

            @Override
            public Optional<StateData<LocalServiceState, LocalServiceInfo>> visit(LocalServiceAdjustInstancesOperation localServiceAdjustInstancesOperation) {
                return Optional.of(StateData.from(currentState, LocalServiceState.ADJUSTING_INSTANCES));
            }

            @Override
            public Optional<StateData<LocalServiceState, LocalServiceInfo>> visit(LocalServiceDeactivateOperation localServiceDeactivateOperation) {
                return Optional.of(StateData.from(currentState, LocalServiceState.DEACTIVATION_REQUESTED));
            }

            @Override
            public Optional<StateData<LocalServiceState, LocalServiceInfo>> visit(LocalServiceRestartOperation localServiceRestartOperation) {
                return Optional.of(StateData.from(currentState, LocalServiceState.REPLACING_INSTANCES));
            }

            @Override
            public Optional<StateData<LocalServiceState, LocalServiceInfo>> visit(
                    LocalServiceUpdateInstanceCountOperation localServiceUpdateInstanceCountOperation) {
                return Optional.of(StateData.from(currentState, LocalServiceState.UPDATING_INSTANCES_COUNT));
            }

            @Override
            public Optional<StateData<LocalServiceState, LocalServiceInfo>> visit(LocalServiceDestroyOperation localServiceDestroyOperation) {
                return Optional.of(StateData.from(currentState, LocalServiceState.DESTROY_REQUESTED));
            }

            @Override
            public Optional<StateData<LocalServiceState, LocalServiceInfo>> visit(LocalServiceActivateOperation localServiceActivateOperation) {
                return Optional.of(StateData.from(currentState, LocalServiceState.ACTIVATION_REQUESTED));
            }

            @Override
            public Optional<StateData<LocalServiceState, LocalServiceInfo>> visit(LocalServiceReplaceInstancesOperation localServiceReplaceInstancesOperation) {
                return Optional.of(StateData.from(currentState, LocalServiceState.REPLACING_INSTANCES));
            }

            @Override
            public Optional<StateData<LocalServiceState, LocalServiceInfo>> visit(LocalServiceStopInstancesOperation localServiceStopInstancesOperation) {
                return Optional.of(StateData.from(currentState, LocalServiceState.STOPPING_INSTANCES));
            }
        });
    }
}
