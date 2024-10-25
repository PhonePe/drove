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
import com.phonepe.drove.controller.statemachine.localservice.LocalServiceAction;
import com.phonepe.drove.controller.statemachine.localservice.LocalServiceActionContext;
import com.phonepe.drove.models.localservice.ActivationState;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.statemachine.StateData;

/**
 *
 */
public abstract class StateUpdatingLocalServiceAction extends LocalServiceAction  {
    private final ActivationState activationState;
    private final LocalServiceState toState;
    private final LocalServiceStateDB stateDB;

    protected StateUpdatingLocalServiceAction(ActivationState activationState, LocalServiceState toState,
                                              LocalServiceStateDB stateDB) {
        this.activationState = activationState;
        this.toState = toState;
        this.stateDB = stateDB;
    }


    @Override
    public final StateData<LocalServiceState, LocalServiceInfo> execute(
            LocalServiceActionContext context,
            StateData<LocalServiceState, LocalServiceInfo> currentState) {
        if(stateDB.updateService(context.getServiceId(), currentState.getData().withState(activationState))) {
            return currentState;
        }
        return StateData.from(currentState, toState);
    }
}
