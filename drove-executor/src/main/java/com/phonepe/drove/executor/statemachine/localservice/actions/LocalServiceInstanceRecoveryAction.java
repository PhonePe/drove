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

import com.codahale.metrics.MetricRegistry;
import com.phonepe.drove.common.model.LocalServiceInstanceSpec;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.model.ExecutorLocalServiceInstanceInfo;
import com.phonepe.drove.executor.statemachine.common.actions.CommonInstanceRecoveryAction;
import com.phonepe.drove.models.application.JobType;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.Map;

/**
 *
 */
@Slf4j
public class LocalServiceInstanceRecoveryAction extends CommonInstanceRecoveryAction<ExecutorLocalServiceInstanceInfo,
        LocalServiceInstanceState, LocalServiceInstanceSpec> {

    @Inject
    public LocalServiceInstanceRecoveryAction(MetricRegistry metricRegistry) {
        super(metricRegistry);
    }

    @Override
    protected StateData<LocalServiceInstanceState, ExecutorLocalServiceInstanceInfo> successState(StateData<LocalServiceInstanceState, ExecutorLocalServiceInstanceInfo> currentState) {
        return StateData.from(currentState, LocalServiceInstanceState.UNREADY);
    }

    @Override
    protected Map<String, String> instanceSpecificFilters(StateData<LocalServiceInstanceState, ExecutorLocalServiceInstanceInfo> currentState) {
        return Map.of(DockerLabels.DROVE_JOB_TYPE_LABEL, JobType.LOCAL_SERVICE.name(),
                      DockerLabels.DROVE_INSTANCE_ID_LABEL, currentState.getData().getInstanceId());    }


    @Override
    protected boolean isStopAllowed() {
        return false;
    }

    @Override
    protected LocalServiceInstanceState defaultErrorState() {
        return LocalServiceInstanceState.STOPPED;
    }

    @Override
    protected LocalServiceInstanceState stoppedState() {
        return LocalServiceInstanceState.STOPPED;
    }
}
