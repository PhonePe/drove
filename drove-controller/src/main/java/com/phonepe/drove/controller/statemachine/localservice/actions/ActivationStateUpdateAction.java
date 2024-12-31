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
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import static com.phonepe.drove.models.localservice.LocalServiceState.ACTIVE;
import static com.phonepe.drove.models.localservice.LocalServiceState.DESTROYED;
import static com.phonepe.drove.models.localservice.LocalServiceState.INACTIVE;

/**
 *
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ActivationStateUpdateAction extends OperationDrivenLocalServiceAction {
    private final LocalServiceStateDB stateDB;

    @Override
    protected StateData<LocalServiceState, LocalServiceInfo> commandReceived(
            LocalServiceActionContext context,
            StateData<LocalServiceState, LocalServiceInfo> currentState,
            LocalServiceOperation operation) {
        return stateDB.service(context.getServiceId())
                .map(existing -> {
                    if (stateDB.updateService(context.getServiceId(),
                                              existing.withState(stateToBeSet()))) {
                        return stateDB.service(context.getServiceId()).orElse(existing);
                    }
                    return existing;
                })
                .map(service -> switch (service.getState()) {
                    case ACTIVE -> StateData.create(ACTIVE, service);
                    case INACTIVE -> StateData.create(INACTIVE, service);
                })
                .orElse(StateData.from(currentState, DESTROYED));

    }

    protected abstract ActivationState stateToBeSet();
}
