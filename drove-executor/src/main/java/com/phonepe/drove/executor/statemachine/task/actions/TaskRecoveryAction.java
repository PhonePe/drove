package com.phonepe.drove.executor.statemachine.task.actions;

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

    @Inject
    public TaskRecoveryAction() {
        //Nothing to do here
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
                .exec(new InstanceLogHandler(MDC.getCopyOfContextMap()
                ));

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
