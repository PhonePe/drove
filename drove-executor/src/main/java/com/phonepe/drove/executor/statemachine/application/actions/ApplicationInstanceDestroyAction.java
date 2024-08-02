/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.common.actions.CommonContainerCleanupAction;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.statemachine.StateData;

/**
 *
 */
public class ApplicationInstanceDestroyAction
        extends CommonContainerCleanupAction<ExecutorInstanceInfo, InstanceState, ApplicationInstanceSpec> {

    public ApplicationInstanceDestroyAction() {
        //Nothing to do here
    }

    @Override
    protected InstanceState defaultErrorState() {
        return InstanceState.DEPROVISIONING;
    }

    @Override
    protected InstanceState stoppedState() {
        return defaultErrorState();
    }

    @Override
    protected StateData<InstanceState, ExecutorInstanceInfo> preRemoveAction(
            InstanceActionContext<ApplicationInstanceSpec> context,
            StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        return StateData.from(currentState, InstanceState.DEPROVISIONING);
    }
}
