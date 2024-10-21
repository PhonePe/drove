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

package com.phonepe.drove.executor.statemachine.application.actions;

import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.common.actions.CommonInstanceSingularHealthCheckAction;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.statemachine.StateData;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Slf4j
@NoArgsConstructor
public class ApplicationInstanceSingularHealthCheckAction extends CommonInstanceSingularHealthCheckAction<ExecutorInstanceInfo, InstanceState,
        ApplicationInstanceSpec> {


    @Override
    protected InstanceState defaultErrorState() {
        return InstanceState.STOPPING;
    }

    @Override
    protected InstanceState stoppedState() {
        return InstanceState.STOPPED;
    }

    @Override
    protected CheckSpec healthcheck(ApplicationInstanceSpec instanceSpec) {
        return instanceSpec.getHealthcheck();
    }

    @Override
    protected StateData<InstanceState, ExecutorInstanceInfo> healthyState(StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        return StateData.from(currentState, InstanceState.HEALTHY);
    }

}
