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
import com.phonepe.drove.executor.engine.InstanceLogHandler;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.application.ApplicationInstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.application.JobType;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.statemachine.StateData;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.slf4j.MDC;

import javax.inject.Inject;
import java.util.Map;

/**
 *
 */
@Slf4j
public class ApplicationInstanceRecoveryAction extends ApplicationInstanceAction {
    private final MetricRegistry metricRegistry;

    @Inject
    public ApplicationInstanceRecoveryAction(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    @Override
    public void stop() {
        //This is not stoppable
    }

    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<InstanceState, ExecutorInstanceInfo> executeImpl(
            InstanceActionContext<ApplicationInstanceSpec> context, StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        val client = context.getClient();
        val instanceId = currentState.getData().getInstanceId();
        val container = client.listContainersCmd()
                .withLabelFilter(
                        Map.of(DockerLabels.DROVE_JOB_TYPE_LABEL, JobType.SERVICE.name(),
                               DockerLabels.DROVE_INSTANCE_ID_LABEL, instanceId))
                .exec()
                .stream()
                .findAny()
                .orElse(null);
        if (null == container) {
            return StateData.errorFrom(currentState,
                                       InstanceState.STOPPED,
                                       "No container found with drove id: " + instanceId);
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

        return StateData.from(currentState, InstanceState.UNREADY);
    }

    @Override
    protected boolean isStopAllowed() {
        return false;
    }

    @Override
    protected InstanceState defaultErrorState() {
        return InstanceState.STOPPED;
    }
}
