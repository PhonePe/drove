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

package com.phonepe.drove.controller.engine;

import com.phonepe.drove.controller.statemachine.localservice.LocalServiceActionContext;
import com.phonepe.drove.controller.statemachine.localservice.LocalServiceStateMachine;
import com.phonepe.drove.models.application.JobType;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.models.operation.LocalServiceOperation;
import com.phonepe.drove.statemachine.Action;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 *
 */
@Slf4j
public class LocalServiceStateMachineExecutor extends StateMachineExecutor<LocalServiceInfo, LocalServiceOperation, LocalServiceState, LocalServiceActionContext, Action<LocalServiceInfo, LocalServiceState, LocalServiceActionContext, LocalServiceOperation>> {
    private static final Set<LocalServiceState> PAUSED_STATES = EnumSet.of(LocalServiceState.ACTIVE, LocalServiceState.INACTIVE);

    public LocalServiceStateMachineExecutor(
            String appId,
            LocalServiceStateMachine stateMachine,
            ExecutorService executorService,
            ControllerRetrySpecFactory retrySpecFactory,
            ConsumingFireForgetSignal<StateMachineExecutor<LocalServiceInfo, LocalServiceOperation, LocalServiceState, LocalServiceActionContext, Action<LocalServiceInfo, LocalServiceState, LocalServiceActionContext, LocalServiceOperation>>> stateMachineCompleted) {
        super(JobType.LOCAL_SERVICE, appId, stateMachine, executorService, retrySpecFactory, stateMachineCompleted);
    }

    @Override
    protected Set<LocalServiceState> pausedStates() {
        return PAUSED_STATES;
    }

    @Override
    protected boolean isTerminal(LocalServiceState state) {
        return state.isTerminal();
    }

}
