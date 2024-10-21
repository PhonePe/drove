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

package com.phonepe.drove.executor.statemachine.common.actions;

import com.codahale.metrics.MetricRegistry;
import com.phonepe.drove.common.model.DeploymentUnitSpec;
import com.phonepe.drove.executor.engine.InstanceLogHandler;
import com.phonepe.drove.executor.model.DeployedExecutionObjectInfo;
import com.phonepe.drove.executor.statemachine.ExecutorActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.slf4j.MDC;

import java.util.Map;

/**
 *
 */
@Slf4j
public abstract class CommonInstanceRecoveryAction<E extends DeployedExecutionObjectInfo, S extends Enum<S>,
        T extends DeploymentUnitSpec> extends ExecutorActionBase<E, S, T> {
    private final MetricRegistry metricRegistry;

    protected CommonInstanceRecoveryAction(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }


    @Override
    public void stop() {
        //This is not stoppable
    }

    @Override
    protected StateData<S, E> executeImpl(InstanceActionContext<T> context, StateData<S, E> currentState) {
        val client = context.getClient();
        val container = client.listContainersCmd()
                .withLabelFilter(instanceSpecificFilters(currentState))
                .exec()
                .stream()
                .findAny()
                .orElse(null);
        if (null == container) {
            return StateData.errorFrom(currentState,
                                       stoppedState(),
                                       "No container found with drove id: " + context.getDockerInstanceId());
        }
        val containerId = container.getId();
        context.setDockerInstanceId(containerId)
                .setDockerImageId(container.getImageId());
        client.logContainerCmd(containerId)
                .withTail(0)
                .withFollowStream(true)
                .withStdOut(true)
                .withStdErr(true)
                .exec(new InstanceLogHandler(
                        MDC.getCopyOfContextMap(),
                        currentState.getData(),
                        metricRegistry
                ));

        return successState(currentState);
    }

    protected abstract StateData<S, E> successState(StateData<S, E> currentState);
    protected abstract Map<String, String> instanceSpecificFilters(final StateData<S, E> currentState);
}
