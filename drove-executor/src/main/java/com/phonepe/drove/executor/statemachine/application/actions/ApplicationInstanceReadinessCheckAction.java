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
import com.phonepe.drove.executor.statemachine.common.actions.CommonInstanceReadinessCheckAction;
import com.phonepe.drove.models.application.CheckResult;
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
public class ApplicationInstanceReadinessCheckAction extends CommonInstanceReadinessCheckAction<ExecutorInstanceInfo, InstanceState,
        ApplicationInstanceSpec> {

    @Override
    protected CheckSpec readinessCheckSpec(ApplicationInstanceSpec spec) {
        return spec.getReadiness();
    }

    @Override
    protected StateData<InstanceState, ExecutorInstanceInfo> handleResult(
            StateData<InstanceState, ExecutorInstanceInfo> currentState,
            CheckResult result) {
        return switch (result.getStatus()) {
            case HEALTHY -> StateData.from(currentState, InstanceState.READY);
            case STOPPED -> StateData.from(currentState, InstanceState.STOPPING);
            case UNHEALTHY -> StateData.errorFrom(currentState,
                                                  InstanceState.READINESS_CHECK_FAILED,
                                                  "Readiness check failed");
        };
    }

    @Override
    protected StateData<InstanceState, ExecutorInstanceInfo> errorState(
            StateData<InstanceState, ExecutorInstanceInfo> currentState,
            Throwable e) {
        return StateData.errorFrom(currentState, InstanceState.READINESS_CHECK_FAILED, e.getMessage());
    }

    @Override
    protected InstanceState defaultErrorState() {
        return InstanceState.READINESS_CHECK_FAILED;
    }

    @Override
    protected InstanceState stoppedState() {
        return InstanceState.STOPPED;
    }

}
