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

package com.phonepe.drove.executor.engine;

import com.github.dockerjava.api.DockerClient;
import com.phonepe.drove.common.model.LocalServiceInstanceSpec;
import com.phonepe.drove.executor.ExecutorActionFactory;
import com.phonepe.drove.executor.managed.ExecutorIdManager;
import com.phonepe.drove.executor.model.ExecutorLocalServiceInstanceInfo;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.statemachine.ExecutorActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.localservice.LocalServiceInstanceStateMachine;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import com.phonepe.drove.statemachine.StateData;
import com.phonepe.drove.statemachine.StateMachine;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Date;
import java.util.concurrent.ExecutorService;

import static com.phonepe.drove.models.instance.LocalServiceInstanceState.*;


/**
 *
 */
@Slf4j
public class LocalServiceInstanceEngine extends InstanceEngine<ExecutorLocalServiceInstanceInfo, LocalServiceInstanceState, LocalServiceInstanceSpec, LocalServiceInstanceInfo> {

    public LocalServiceInstanceEngine(
            final ExecutorIdManager executorIdManager, ExecutorService service,
            ExecutorActionFactory<ExecutorLocalServiceInstanceInfo,
                    LocalServiceInstanceState, LocalServiceInstanceSpec> actionFactory,
            ResourceManager resourceDB, DockerClient client) {
        super(executorIdManager, service, actionFactory, resourceDB, client);
    }

    @Override
    protected StateData<LocalServiceInstanceState, ExecutorLocalServiceInstanceInfo> createInitialState(
            LocalServiceInstanceSpec spec,
            Date currDate,
            ExecutorIdManager executorIdManager) {
        return StateData.create(PENDING,
                                new ExecutorLocalServiceInstanceInfo(spec.getServiceId(),
                                                         spec.getServiceName(),
                                                         spec.getInstanceId(),
                                                         executorIdManager.executorId().orElse(null),
                                                         null,
                                                         spec.getResources(),
                                                         Collections.emptyMap(),
                                                         currDate,
                                                         currDate));
    }

    @Override
    protected LocalServiceInstanceState lostState() {
        return LOST;
    }

    @Override
    protected boolean isTerminal(LocalServiceInstanceState state) {
        return state.isTerminal();
    }

    @Override
    protected boolean isError(LocalServiceInstanceState state) {
        return state.isError();
    }

    @Override
    protected boolean isRunning(LocalServiceInstanceState state) {
        return LocalServiceInstanceState.RUNNING_STATES.contains(state);
    }

    @Override
    protected StateMachine<ExecutorLocalServiceInstanceInfo, Void, LocalServiceInstanceState, InstanceActionContext<LocalServiceInstanceSpec>, ExecutorActionBase<ExecutorLocalServiceInstanceInfo, LocalServiceInstanceState, LocalServiceInstanceSpec>> createStateMachine(
            String executorId,
            LocalServiceInstanceSpec spec,
            StateData<LocalServiceInstanceState, ExecutorLocalServiceInstanceInfo> currentState,
            ExecutorActionFactory<ExecutorLocalServiceInstanceInfo, LocalServiceInstanceState, LocalServiceInstanceSpec> actionFactory,
            DockerClient client) {
        return new LocalServiceInstanceStateMachine(executorId,
                                                    spec,
                                                    currentState,
                                                    actionFactory,
                                                    client,
                                                    currentState.getState().equals(UNKNOWN));
    }

    @Override
    protected LocalServiceInstanceInfo convertStateToInstanceInfo(StateData<LocalServiceInstanceState, ExecutorLocalServiceInstanceInfo> currentState) {
        return ExecutorUtils.convertToLocalServiceInstance(currentState);
    }

}
