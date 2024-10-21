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

import com.codahale.metrics.MetricRegistry;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.common.actions.CommonInstanceRecoveryAction;
import com.phonepe.drove.models.application.JobType;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.Map;

/**
 *
 */
@Slf4j
public class ApplicationInstanceRecoveryAction extends CommonInstanceRecoveryAction<ExecutorInstanceInfo, InstanceState,
        ApplicationInstanceSpec> {

    @Inject
    public ApplicationInstanceRecoveryAction(MetricRegistry metricRegistry) {
        super(metricRegistry);
    }

    @Override
    protected StateData<InstanceState, ExecutorInstanceInfo> successState(StateData<InstanceState,
            ExecutorInstanceInfo> currentState) {
        return StateData.from(currentState, InstanceState.UNREADY);
    }

    @Override
    protected Map<String, String> instanceSpecificFilters(StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        return Map.of(DockerLabels.DROVE_JOB_TYPE_LABEL, JobType.SERVICE.name(),
               DockerLabels.DROVE_INSTANCE_ID_LABEL, currentState.getData().getInstanceId());
    }

    @Override
    protected boolean isStopAllowed() {
        return false;
    }

    @Override
    protected InstanceState defaultErrorState() {
        return InstanceState.STOPPED;
    }

    @Override
    protected InstanceState stoppedState() {
        return InstanceState.STOPPED;
    }
}
