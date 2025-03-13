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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.common.net.HttpCaller;
import com.phonepe.drove.executor.ExecutorOptions;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.engine.InstanceLogHandler;
import com.phonepe.drove.executor.model.ExecutorTaskInfo;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.task.TaskAction;
import com.phonepe.drove.executor.utils.DockerUtils;
import com.phonepe.drove.models.application.JobType;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import com.phonepe.drove.models.taskinstance.TaskResult;
import com.phonepe.drove.models.taskinstance.TaskState;
import com.phonepe.drove.statemachine.StateData;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.slf4j.MDC;

import javax.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.phonepe.drove.common.CommonUtils.hostname;
import static com.phonepe.drove.common.CommonUtils.instanceId;
import static com.phonepe.drove.executor.utils.ExecutorUtils.injectResult;

/**
 *
 */
@Slf4j
public class TaskRunAction extends TaskAction {

    private final ResourceConfig schedulingConfig;
    private final ExecutorOptions executorOptions;
    private final HttpCaller httpCaller;
    private final ObjectMapper mapper;
    private final MetricRegistry metricRegistry;
    private final ResourceManager resourceManager;

    @Inject
    public TaskRunAction(ResourceConfig resourceConfig,
                         ExecutorOptions executorOptions, HttpCaller httpCaller,
                         ObjectMapper mapper,
                         MetricRegistry metricRegistry,
                         ResourceManager resourceManager) {
        this.schedulingConfig = resourceConfig;
        this.executorOptions = executorOptions;
        this.httpCaller = httpCaller;
        this.mapper = mapper;
        this.metricRegistry = metricRegistry;
        this.resourceManager = resourceManager;
    }

    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<TaskState, ExecutorTaskInfo> executeImpl(
            InstanceActionContext<TaskInstanceSpec> context,
            StateData<TaskState, ExecutorTaskInfo> currentState) {
        val instanceSpec = context.getInstanceSpec();
        val client = context.getClient();
        try {
            val instanceInfoRef = new AtomicReference<ExecutorTaskInfo>();
            val instanceId = instanceId(instanceSpec);
            val containerId = DockerUtils.createContainer(
                    schedulingConfig,
                    client, CommonUtils.instanceId(context.getInstanceSpec()),
                    instanceSpec,
                    params -> {
                        val instanceInfo = instanceInfo(currentState,
                                                        instanceSpec.getResources(),
                                                        params.getHostname(),
                                                        currentState.getData());
                        instanceInfoRef.set(instanceInfo);
                        val labels = params.getCustomLabels();
                        labels.put(DockerLabels.DROVE_JOB_TYPE_LABEL, JobType.COMPUTATION.name());
                        labels.put(DockerLabels.DROVE_INSTANCE_ID_LABEL, instanceId);
                        labels.put(DockerLabels.DROVE_INSTANCE_SPEC_LABEL, mapper.writeValueAsString(instanceSpec));
                        labels.put(DockerLabels.DROVE_INSTANCE_DATA_LABEL, mapper.writeValueAsString(instanceInfo));

                        val env = params.getCustomEnv();
                        env.add("DROVE_EXECUTOR_HOST=" + hostname());
                        env.add("DROVE_TASK_ID=" + instanceSpec.getTaskId());
                        env.add("DROVE_INSTANCE_ID=" + instanceSpec.getInstanceId());
                        env.add("DROVE_SOURCE_APP_NAME=" + instanceSpec.getSourceAppName());
                    },
                    executorOptions,
                    resourceManager);
            DockerUtils.injectConfigs(containerId, context.getClient(), instanceSpec, httpCaller);
            context.setDockerInstanceId(containerId);
            client.startContainerCmd(containerId)
                    .exec();
            client.logContainerCmd(containerId)
                    .withTailAll()
                    .withFollowStream(true)
                    .withStdOut(true)
                    .withStdErr(true)
                    .exec(new InstanceLogHandler(MDC.getCopyOfContextMap(),
                                                 instanceInfoRef.get(),
                                                 metricRegistry
                    ));
            return StateData.create(TaskState.RUNNING, instanceInfoRef.get());
        }
        catch (Exception e) {
            log.error("Error creating container: ", e);
            return StateData.errorFrom(injectResult(currentState, new TaskResult(TaskResult.Status.FAILED, -1)),
                                                    TaskState.RUN_FAILED, e.getMessage());
        }    }

    @Override
    protected TaskState defaultErrorState() {
        return TaskState.RUN_FAILED;
    }

    private ExecutorTaskInfo instanceInfo(
            StateData<TaskState, ExecutorTaskInfo> currentState,
            List<ResourceAllocation> resources,
            String hostName,
            ExecutorTaskInfo oldData) {
        val data = currentState.getData();
        val currTime = new Date();
        return new ExecutorTaskInfo(
                data.getTaskId(),
                data.getSourceAppName(),
                data.getInstanceId(),
                data.getExecutorId(),
                hostName,
                data.getExecutable(),
                resources,
                data.getVolumes(),
                data.getLoggingSpec(),
                data.getEnv(),
                data.getMetadata(),
                data.getTaskResult(),
                null == oldData ? currTime : oldData.getCreated(),
                currTime);
    }

    @Override
    public void stop() {
        //Nothing to do here
    }
}
