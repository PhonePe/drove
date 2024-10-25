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

package com.phonepe.drove.controller.statemachine.localservice;

import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.statemachine.common.actions.AsyncAction;
import com.phonepe.drove.jobexecutor.JobExecutor;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.models.operation.LocalServiceOperation;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Slf4j
public abstract class LocalServiceAsyncAction extends AsyncAction<LocalServiceInfo, LocalServiceState, LocalServiceActionContext, LocalServiceOperation> {
    protected final LocalServiceStateDB stateDB;

    protected LocalServiceAsyncAction(JobExecutor<Boolean> jobExecutor, LocalServiceStateDB stateDB) {
        super(jobExecutor);
        this.stateDB = stateDB;
    }

    @Override
    protected StateData<LocalServiceState, LocalServiceInfo> handleEmptyTopology(
            LocalServiceActionContext context,
            StateData<LocalServiceState, LocalServiceInfo> currentState) {
        return StateData.from(currentState, LocalServiceState.ACTIVE);
    }

    @Override
    protected StateData<LocalServiceState, LocalServiceInfo> errorState(
            StateData<LocalServiceState, LocalServiceInfo> currentState,
            String message) {
        return StateData.errorFrom(currentState, LocalServiceState.ACTIVE, message);
    }
}
