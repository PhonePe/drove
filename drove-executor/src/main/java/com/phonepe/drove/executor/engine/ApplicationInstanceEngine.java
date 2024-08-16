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
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.ExecutorActionFactory;
import com.phonepe.drove.executor.managed.ExecutorIdManager;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.statemachine.ExecutorActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.application.ApplicationInstanceStateMachine;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.statemachine.StateData;
import com.phonepe.drove.statemachine.StateMachine;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Date;
import java.util.concurrent.ExecutorService;

import static com.phonepe.drove.models.instance.InstanceState.*;

/**
 *
 */
@Slf4j
public class ApplicationInstanceEngine extends InstanceEngine<ExecutorInstanceInfo, InstanceState, ApplicationInstanceSpec, InstanceInfo> {


    public ApplicationInstanceEngine(
            final ExecutorIdManager executorIdManager, ExecutorService service,
            ExecutorActionFactory<ExecutorInstanceInfo, InstanceState, ApplicationInstanceSpec> actionFactory,
            ResourceManager resourceDB, DockerClient client) {
        super(executorIdManager, service, actionFactory, resourceDB, client);
    }

    @Override
    protected StateData<InstanceState, ExecutorInstanceInfo> createInitialState(
            ApplicationInstanceSpec spec,
            Date currDate,
            ExecutorIdManager executorIdManager) {
        return StateData.create(InstanceState.PENDING,
                                new ExecutorInstanceInfo(spec.getAppId(),
                                                         spec.getAppName(),
                                                         spec.getInstanceId(),
                                                         executorIdManager.executorId().orElse(null),
                                                         null,
                                                         spec.getResources(),
                                                         Collections.emptyMap(),
                                                         currDate,
                                                         currDate));
    }

    @Override
    protected InstanceState lostState() {
        return LOST;
    }

    @Override
    protected boolean isTerminal(InstanceState state) {
        return state.isTerminal();
    }

    @Override
    protected boolean isError(InstanceState state) {
        return state.isError();
    }

    @Override
    protected boolean isRunning(InstanceState state) {
        return RUNNING_STATES.contains(state);
    }

    @Override
    protected StateMachine<ExecutorInstanceInfo, Void, InstanceState, InstanceActionContext<ApplicationInstanceSpec>,
            ExecutorActionBase<ExecutorInstanceInfo, InstanceState, ApplicationInstanceSpec>> createStateMachine(
            String executorId,
            ApplicationInstanceSpec spec,
            StateData<InstanceState, ExecutorInstanceInfo> currentState,
            ExecutorActionFactory<ExecutorInstanceInfo, InstanceState, ApplicationInstanceSpec> actionFactory,
            DockerClient client) {
        return new ApplicationInstanceStateMachine(executorId,
                                                   spec,
                                                   currentState,
                                                   actionFactory,
                                                   client,
                                                   currentState.getState().equals(UNKNOWN));
    }

    @Override
    protected InstanceInfo convertStateToInstanceInfo(StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        return ExecutorUtils.convert(currentState);
    }
}
