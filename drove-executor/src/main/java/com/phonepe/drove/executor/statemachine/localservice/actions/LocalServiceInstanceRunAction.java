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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.common.model.LocalServiceInstanceSpec;
import com.phonepe.drove.common.net.HttpCaller;
import com.phonepe.drove.executor.ExecutorOptions;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.model.ExecutorLocalServiceInstanceInfo;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.statemachine.common.actions.CommonInstanceRunAction;
import com.phonepe.drove.models.application.JobType;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import java.util.*;

/**
 *
 */
@Slf4j
public class LocalServiceInstanceRunAction extends CommonInstanceRunAction<ExecutorLocalServiceInstanceInfo,
        LocalServiceInstanceState, LocalServiceInstanceSpec> {

    @Inject
    public LocalServiceInstanceRunAction(
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
    protected StateData<LocalServiceInstanceState, ExecutorLocalServiceInstanceInfo> successState(
            StateData<LocalServiceInstanceState, ExecutorLocalServiceInstanceInfo> currentState,
            ExecutorLocalServiceInstanceInfo instanceInfo) {
        return StateData.create(LocalServiceInstanceState.UNREADY, instanceInfo);

    }

    @Override
    protected Map<String, String> instanceSpecificLabels(LocalServiceInstanceSpec spec) {
        return Map.of(
                DockerLabels.DROVE_JOB_TYPE_LABEL, JobType.LOCAL_SERVICE.name(),
                DockerLabels.DROVE_INSTANCE_ID_LABEL, spec.getInstanceId());
    }

    @Override
    protected List<String> instanceSpecificEnv(LocalServiceInstanceSpec spec) {
        return List.of("DROVE_SERVICE_ID=" + spec.getServiceId(),
                       "DROVE_SERVICE_NAME=" + spec.getServiceName(),
                       "DROVE_SERVICE_INSTANCE_ID=" + spec.getInstanceId(),
                       "DROVE_APP_INSTANCE_AUTH_TOKEN=" + spec.getInstanceAuthToken());
    }

    @Override
    protected ExecutorLocalServiceInstanceInfo instanceInfo(
            StateData<LocalServiceInstanceState, ExecutorLocalServiceInstanceInfo> currentState,
            HashMap<String, InstancePort> portMappings,
            List<ResourceAllocation> resources,
            String hostname,
            ExecutorLocalServiceInstanceInfo oldData) {
        val data = currentState.getData();
        return new ExecutorLocalServiceInstanceInfo(
                data.getServiceId(),
                data.getServiceName(),
                data.getInstanceId(),
                data.getExecutorId(),
                new LocalInstanceInfo(hostname, portMappings),
                resources,
                Collections.emptyMap(),
                null == oldData
                ? new Date()
                : oldData.getCreated(),
                new Date());
    }

    @Override
    protected LocalServiceInstanceState defaultErrorState() {
        return LocalServiceInstanceState.START_FAILED;
    }

    @Override
    protected StateData<LocalServiceInstanceState, ExecutorLocalServiceInstanceInfo> errorState(
            StateData<LocalServiceInstanceState, ExecutorLocalServiceInstanceInfo> currentState,
            Throwable e) {
        return StateData.errorFrom(currentState, LocalServiceInstanceState.START_FAILED, e.getMessage());
    }

    @Override
    protected LocalServiceInstanceState stoppedState() {
        return LocalServiceInstanceState.STOPPED;
    }
}
