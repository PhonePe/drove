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

package com.phonepe.drove.executor.statemachine.task.actions;

import com.codahale.metrics.MetricRegistry;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.engine.InstanceLogHandler;
import com.phonepe.drove.executor.model.ExecutorTaskInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.task.TaskAction;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import com.phonepe.drove.models.application.JobType;
import com.phonepe.drove.models.taskinstance.TaskState;
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
public class TaskRecoveryAction extends TaskAction {
    private final MetricRegistry metricRegistry;

    @Inject
    public TaskRecoveryAction(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    @Override
    public void stop() {
        //This is not stoppable
    }

    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<TaskState, ExecutorTaskInfo> executeImpl(
            InstanceActionContext<TaskInstanceSpec> context, StateData<TaskState, ExecutorTaskInfo> currentState) {
        val client = context.getClient();
        val instanceId = ExecutorUtils.instanceId(currentState.getData());
        val container = client.listContainersCmd()
                .withLabelFilter(
                        Map.of(DockerLabels.DROVE_JOB_TYPE_LABEL, JobType.COMPUTATION.name(),
                               DockerLabels.DROVE_INSTANCE_ID_LABEL, instanceId))
                .exec()
                .stream()
                .findAny()
                .orElse(null);
        if (null == container) {
            return StateData.errorFrom(currentState,
                                       TaskState.STOPPED,
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
                .exec(new InstanceLogHandler(MDC.getCopyOfContextMap(),
                                             currentState.getData(),
                                             metricRegistry));

        return StateData.from(currentState, TaskState.RUNNING);
    }

    @Override
    protected boolean isStopAllowed() {
        return false;
    }

    @Override
    protected TaskState defaultErrorState() {
        return TaskState.STOPPED;
    }
}
