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

package com.phonepe.drove.executor.statemachine.localservice.actions;

import com.phonepe.drove.common.model.LocalServiceInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorLocalServiceInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.localservice.LocalServiceInstanceAction;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.statemachine.StateData;
import io.appform.functionmetrics.MonitoredFunction;

/**
 * Validates spec. For now does nothing
 */
public class LocalServiceInstanceSpecValidator extends LocalServiceInstanceAction {

    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<LocalServiceInstanceState, ExecutorLocalServiceInstanceInfo> executeImpl(
            InstanceActionContext<LocalServiceInstanceSpec> context,
            StateData<LocalServiceInstanceState, ExecutorLocalServiceInstanceInfo> currentState) {
        return StateData.from(currentState, LocalServiceInstanceState.PROVISIONING);
    }

    @Override
    protected LocalServiceInstanceState defaultErrorState() {
        return LocalServiceInstanceState.STOPPING;
    }

    @Override
    public void stop() {
        //Ignore this
    }
}
