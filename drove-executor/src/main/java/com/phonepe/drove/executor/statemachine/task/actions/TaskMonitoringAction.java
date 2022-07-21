package com.phonepe.drove.executor.statemachine.task.actions;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorTaskInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.task.TaskInstanceAction;
import com.phonepe.drove.models.taskinstance.TaskInstanceState;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

import javax.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
@Slf4j
public class TaskMonitoringAction extends TaskInstanceAction {
    private final ExecutorService taskMonitoringPool;
    private final AtomicBoolean stopped = new AtomicBoolean();

    @Inject
    public TaskMonitoringAction(ExecutorService taskMonitoringPool) {
        this.taskMonitoringPool = taskMonitoringPool;
    }

    @Override
    protected StateData<TaskInstanceState, ExecutorTaskInstanceInfo> executeImpl(
            InstanceActionContext<TaskInstanceSpec> context,
            StateData<TaskInstanceState, ExecutorTaskInstanceInfo> currentState) {
        val spec = context.getInstanceSpec();
        val retryPolicy = new RetryPolicy<InspectContainerResponse.ContainerState>()
                .handle(Exception.class)
                .withDelay(Duration.ofSeconds(1))
                .handleResultIf(result -> null == result || Objects.requireNonNullElse(result.getRunning(), true));
        val dockerClient = context.getClient();
        val containerId = context.getDockerInstanceId();
        try {
            val result = Failsafe.with(List.of(retryPolicy))
                    .get(() -> dockerClient.inspectContainerCmd(containerId)
                            .exec()
                            .getState());
            if(stopped.get()) {
                return StateData.from(currentState, TaskInstanceState.RUN_CANCELLED);
            }
            log.info("Task instance completed with status: {}", result);
            val exitCode = result.getExitCodeLong();
            if (exitCode == 0) {
                return StateData.from(currentState, TaskInstanceState.RUN_COMPLETED);
            }
            return StateData.errorFrom(currentState,
                                       TaskInstanceState.RUN_FAILED,
                                       "Task instance exited with status: " + exitCode);
        }
        catch (Exception e) {
            return StateData.errorFrom(currentState, TaskInstanceState.RUN_FAILED, e.getMessage());
        }
    }

    @Override
    protected TaskInstanceState defaultErrorState() {
        return TaskInstanceState.RUN_FAILED;
    }

    @Override
    public void stop() {
        stopped.set(true);
    }

}
