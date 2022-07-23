package com.phonepe.drove.executor.statemachine.task.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.engine.InstanceLogHandler;
import com.phonepe.drove.executor.logging.LogBus;
import com.phonepe.drove.executor.model.ExecutorTaskInstanceInfo;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.task.TaskInstanceAction;
import com.phonepe.drove.executor.utils.DockerUtils;
import com.phonepe.drove.models.application.JobType;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import com.phonepe.drove.models.taskinstance.TaskInstanceState;
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

/**
 *
 */
@Slf4j
public class TaskInstanceRunAction extends TaskInstanceAction {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final LogBus logBus;
    private final ResourceConfig schedulingConfig;

    @Inject
    public TaskInstanceRunAction(LogBus logBus, ResourceConfig resourceConfig) {
        this.logBus = logBus;
        this.schedulingConfig = resourceConfig;
    }

    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<TaskInstanceState, ExecutorTaskInstanceInfo> executeImpl(
            InstanceActionContext<TaskInstanceSpec> context,
            StateData<TaskInstanceState, ExecutorTaskInstanceInfo> currentState) {
        val instanceSpec = context.getInstanceSpec();
        val client = context.getClient();
        try {
            val instanceInfoRef = new AtomicReference<ExecutorTaskInstanceInfo>();
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
                        labels.put(DockerLabels.DROVE_INSTANCE_SPEC_LABEL, MAPPER.writeValueAsString(instanceSpec));
                        labels.put(DockerLabels.DROVE_INSTANCE_DATA_LABEL, MAPPER.writeValueAsString(instanceInfo));

                        val env = params.getCustomEnv();
                        env.add("DROVE_EXECUTOR_HOST=" + hostname());
                        env.add("DROVE_TASK_ID=" + instanceSpec.getTaskId());
                        env.add("DROVE_INSTANCE_ID=" + instanceSpec.getInstanceId());
                        env.add("DROVE_SOURCE_APP_NAME=" + instanceSpec.getSourceAppName());
                    });

            context.setDockerInstanceId(containerId);
            client.startContainerCmd(containerId)
                    .exec();
            client.logContainerCmd(containerId)
                    .withTailAll()
                    .withFollowStream(true)
                    .withStdOut(true)
                    .withStdErr(true)
                    .exec(new InstanceLogHandler(MDC.getCopyOfContextMap(),
                                                 instanceSpec.getSourceAppName(),
                                                 instanceId,
                                                 logBus));
            return StateData.create(TaskInstanceState.RUNNING, instanceInfoRef.get());
        }
        catch (Exception e) {
            log.error("Error creating container: ", e);
            return StateData.errorFrom(currentState, TaskInstanceState.RUN_FAILED, e.getMessage());
        }    }

    @Override
    protected TaskInstanceState defaultErrorState() {
        return TaskInstanceState.RUN_FAILED;
    }

    private ExecutorTaskInstanceInfo instanceInfo(
            StateData<TaskInstanceState, ExecutorTaskInstanceInfo> currentState,
            List<ResourceAllocation> resources,
            String hostName,
            ExecutorTaskInstanceInfo oldData) {
        val data = currentState.getData();
        return new ExecutorTaskInstanceInfo(
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
                null == oldData
                ? new Date()
                : oldData.getCreated(),
                new Date());
    }

    @Override
    public void stop() {
        //Nothing to do here
    }
}
