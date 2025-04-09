/*
 *  Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
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
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.models.operation.LocalServiceOperation;
import com.phonepe.drove.statemachine.StateData;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.val;

import static com.phonepe.drove.models.localservice.LocalServiceState.*;

/**
 *
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class UpdatingLocalServiceAction extends OperationDrivenLocalServiceAction {
    private final LocalServiceStateDB stateDB;

    @Override
    protected final StateData<LocalServiceState, LocalServiceInfo> commandReceived(
            LocalServiceActionContext context,
            StateData<LocalServiceState, LocalServiceInfo> currentState,
            LocalServiceOperation operation) {
        return stateDB.service(context.getServiceId())
                .map(existing -> {
                    if (stateDB.updateService(context.getServiceId(),
                                              update(context, currentState, operation, existing))) {
                        return stateDB.service(context.getServiceId()).orElse(existing);
                    }
                    return existing;
                })
                .map(service -> {
                    val state = ControllerUtils.serviceActivationStateToSMState(service.getActivationState());
                    return StateData.create(state, service);
                })
                .orElse(StateData.from(currentState, DESTROYED));
    }

    protected abstract LocalServiceInfo update(
            LocalServiceActionContext context,
            StateData<LocalServiceState, LocalServiceInfo> currentState,
            LocalServiceOperation operation,
            LocalServiceInfo existing);

}
