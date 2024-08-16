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

package com.phonepe.drove.executor.statemachine;


import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.DeploymentUnitSpec;
import com.phonepe.drove.executor.model.DeployedExecutionObjectInfo;
import com.phonepe.drove.statemachine.Action;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 *
 */
@Slf4j
public abstract class ExecutorActionBase<E extends DeployedExecutionObjectInfo, S extends Enum<S>, T extends DeploymentUnitSpec> implements Action<E, S, InstanceActionContext<T>, Void> {

    @Override
    public final StateData<S, E> execute(
            InstanceActionContext<T> context,
            StateData<S, E> currentState) {
        if (isStopAllowed() && context.getAlreadyStopped().get()) {
            return StateData.from(currentState, stoppedState());
        }
        try {
            return executeImpl(context, currentState);
        }
        catch (Exception e) {
            val instanceSpec = context.getInstanceSpec();
            log.error("Error running action implementation for " + CommonUtils.instanceId(instanceSpec), e);
            return StateData.errorFrom(currentState, defaultErrorState(), e.getMessage());
        }
    }

    protected abstract StateData<S, E> executeImpl(
            InstanceActionContext<T> context, StateData<S, E> currentState);

    protected boolean isStopAllowed() {
        return true;
    }

    protected abstract S defaultErrorState();

    protected abstract S stoppedState();
}
