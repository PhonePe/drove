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
import com.phonepe.drove.statemachine.StateData;

/**
 *
 */
public class DestroyLocalServiceAction extends LocalServiceAction {
    @Override
    public StateData<LocalServiceState, LocalServiceInfo> execute(
            LocalServiceActionContext context,
            StateData<LocalServiceState, LocalServiceInfo> currentState) {
        return StateData.from(currentState, LocalServiceState.DESTROYED);
    }
}
