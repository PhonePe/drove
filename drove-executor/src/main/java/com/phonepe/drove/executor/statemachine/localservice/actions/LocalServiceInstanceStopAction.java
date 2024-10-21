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
import com.phonepe.drove.executor.statemachine.common.actions.CommonInstanceStopAction;
import com.phonepe.drove.models.application.PreShutdownSpec;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Slf4j
public class LocalServiceInstanceStopAction
        extends CommonInstanceStopAction<ExecutorLocalServiceInstanceInfo,
        LocalServiceInstanceState, LocalServiceInstanceSpec> {

    @Override
    protected LocalServiceInstanceState defaultErrorState() {
        return LocalServiceInstanceState.DEPROVISIONING;
    }

    @Override
    protected PreShutdownSpec preShutdownSpec(LocalServiceInstanceSpec spec) {
        return spec.getPreShutdown();
    }

}
