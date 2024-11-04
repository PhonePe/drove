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

import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.statemachine.localservice.LocalServiceActionContext;
import com.phonepe.drove.controller.statemachine.localservice.OperationDrivenLocalServiceAction;
import com.phonepe.drove.models.localservice.ActivationState;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.models.operation.LocalServiceOperation;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;

/**
 *
 */
@Slf4j
public class ActivateLocalServiceAction extends OperationDrivenLocalServiceAction {
    private final LocalServiceStateDB stateDB;

    @Inject
    public ActivateLocalServiceAction(LocalServiceStateDB stateDB) {
        this.stateDB = stateDB;
    }

    @Override
    protected StateData<LocalServiceState, LocalServiceInfo> commandReceived(
            LocalServiceActionContext context,
            StateData<LocalServiceState, LocalServiceInfo> currentState,
            LocalServiceOperation operation) {
        val activationState = stateDB.service(context.getServiceId())
                .map(LocalServiceInfo::getState)
                .orElse(ActivationState.UNKNOWN);
        val toState = switch (activationState) {
            case ACTIVE -> LocalServiceState.ACTIVE;
            case INACTIVE, UNKNOWN -> {
                if (stateDB.updateService(context.getServiceId(),
                                          currentState.getData().withState(ActivationState.ACTIVE))) {
                    yield LocalServiceState.ACTIVE;
                }
                yield LocalServiceState.DESTROYED;
            }
        };

        return StateData.create(toState,
                                stateDB.service(context.getServiceId())
                                        .orElse(currentState.getData().withState(ActivationState.ACTIVE)));
    }
}
