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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.common.net.HttpCaller;
import com.phonepe.drove.executor.ExecutorOptions;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.statemachine.common.actions.CommonInstanceRunAction;
import com.phonepe.drove.models.application.JobType;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import java.util.*;

/**
 *
 */
@Slf4j
public class ApplicationInstanceRunAction extends CommonInstanceRunAction<ExecutorInstanceInfo, InstanceState,
        ApplicationInstanceSpec> {

    @Inject
    public ApplicationInstanceRunAction(
            ResourceConfig resourceConfig,
            ExecutorOptions executorOptions,
            HttpCaller httpCaller,
            ObjectMapper mapper,
            MetricRegistry metricRegistry,
            ResourceManager resourceManager) {
        super(resourceConfig,
              executorOptions,
              httpCaller,
              mapper,
              metricRegistry,
              resourceManager);
    }

    @Override
    protected StateData<InstanceState, ExecutorInstanceInfo> successState(
            StateData<InstanceState, ExecutorInstanceInfo> currentState,
            ExecutorInstanceInfo instanceInfo) {
        return StateData.create(InstanceState.UNREADY, instanceInfo);
    }

    @Override
    protected StateData<InstanceState, ExecutorInstanceInfo> errorState(
            StateData<InstanceState, ExecutorInstanceInfo> currentState,
            Throwable e) {
        return StateData.errorFrom(currentState, InstanceState.START_FAILED, e.getMessage());
    }

    @Override
    protected Map<String, String> instanceSpecificLabels(ApplicationInstanceSpec spec) {
        return Map.of(
                DockerLabels.DROVE_JOB_TYPE_LABEL, JobType.SERVICE.name(),
                DockerLabels.DROVE_INSTANCE_ID_LABEL, spec.getInstanceId());
    }

    @Override
    protected List<String> instanceSpecificEnv(ApplicationInstanceSpec spec) {
        return List.of(
                "DROVE_INSTANCE_ID=" + spec.getInstanceId(),
                "DROVE_APP_ID=" + spec.getAppId(),
                "DROVE_APP_NAME=" + spec.getAppName(),
                "DROVE_APP_INSTANCE_AUTH_TOKEN=" + spec.getInstanceAuthToken());
    }

    @Override
    protected InstanceState defaultErrorState() {
        return InstanceState.START_FAILED;
    }

    @Override
    protected InstanceState stoppedState() {
        return InstanceState.STOPPED;
    }

    protected ExecutorInstanceInfo instanceInfo(
            StateData<InstanceState, ExecutorInstanceInfo> currentState,
            HashMap<String, InstancePort> portMappings,
            List<ResourceAllocation> resources,
            String hostName,
            ExecutorInstanceInfo oldData) {
        val data = currentState.getData();
        return new ExecutorInstanceInfo(
                data.getAppId(),
                data.getAppName(),
                data.getInstanceId(),
                data.getExecutorId(),
                new LocalInstanceInfo(hostName, portMappings),
                resources,
                Collections.emptyMap(),
                null == oldData
                ? new Date()
                : oldData.getCreated(),
                new Date());
    }
}
