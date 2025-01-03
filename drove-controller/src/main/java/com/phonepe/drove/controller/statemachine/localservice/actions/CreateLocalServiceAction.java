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
public class CreateLocalServiceAction extends OperationDrivenLocalServiceAction {
    private final LocalServiceStateDB stateDB;

    @Inject
    public CreateLocalServiceAction(LocalServiceStateDB stateDB) {
        this.stateDB = stateDB;
    }

    @Override
    public StateData<LocalServiceState, LocalServiceInfo> commandReceived(
            LocalServiceActionContext context,
            StateData<LocalServiceState, LocalServiceInfo> currentState,
            LocalServiceOperation operation) {
        val existing = stateDB.service(context.getServiceId())
                .orElse(null);
        if(null == existing) { //Hit in create path
            if(stateDB.updateService(context.getServiceId(), currentState.getData().withActivationState(ActivationState.INACTIVE))) {
                val updated = stateDB.service(context.getServiceId()).orElse(null);
                if(null != updated) {
                    return StateData.create(LocalServiceState.INACTIVE, updated);
                }
            }
        }
        else { //Will be hit in recovery path
            val toState = switch (existing.getActivationState()) {
                case ACTIVE -> LocalServiceState.ACTIVE;
                case INACTIVE -> LocalServiceState.INACTIVE;
            };
            return StateData.create(toState, existing);
        }
        //Should not ever happen
        return StateData.from(currentState, LocalServiceState.DESTROYED);
    }
}
