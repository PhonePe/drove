package com.phonepe.drove.executor.statemachine.task;

import com.github.dockerjava.api.DockerClient;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.ExecutorActionFactory;
import com.phonepe.drove.executor.model.ExecutorTaskInfo;
import com.phonepe.drove.executor.statemachine.ExecutorActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.task.actions.*;
import com.phonepe.drove.models.taskinstance.TaskState;
import com.phonepe.drove.statemachine.StateData;
import com.phonepe.drove.statemachine.StateMachine;
import com.phonepe.drove.statemachine.Transition;
import lombok.NonNull;

import java.util.List;

import static com.phonepe.drove.models.taskinstance.TaskState.*;


/**
 *
 */
public class TaskStateMachine extends StateMachine<ExecutorTaskInfo, Void, TaskState,
        InstanceActionContext<TaskInstanceSpec>, ExecutorActionBase<ExecutorTaskInfo, TaskState,
                TaskInstanceSpec>> {
    private static final List<Transition<ExecutorTaskInfo, Void, TaskState,
            InstanceActionContext<TaskInstanceSpec>, ExecutorActionBase<ExecutorTaskInfo, TaskState,
                        TaskInstanceSpec>>> transitions
            = List.of(
            new Transition<>(PENDING,
                             TaskSpecValidator.class,
                             PROVISIONING,
                             STOPPING),
            new Transition<>(PROVISIONING,
                             TaskExecutableFetchAction.class,
                             STARTING,
                             PROVISIONING_FAILED),
            new Transition<>(STARTING,
                             TaskRunAction.class,
                             RUNNING,
                             RUN_FAILED),
            new Transition<>(RUNNING,
                             TaskMonitoringAction.class,
                             RUN_COMPLETED,
                             RUN_CANCELLED,
                             RUN_FAILED),
            new Transition<>(RUN_COMPLETED,
                             TaskDestroyAction.class,
                             DEPROVISIONING),
            new Transition<>(RUN_FAILED,
                             TaskDestroyAction.class,
                             DEPROVISIONING),
            new Transition<>(RUN_CANCELLED,
                             TaskDestroyAction.class,
                             DEPROVISIONING),
            new Transition<>(DEPROVISIONING,
                             TaskExecutableCleanupAction.class,
                             STOPPED),
            new Transition<>(UNKNOWN,
                             TaskRecoveryAction.class,
                             RUNNING,
                             STOPPED)
                     );

    public TaskStateMachine(
            String executorId,
            TaskInstanceSpec instanceSpec,
            @NonNull StateData<TaskState, ExecutorTaskInfo> initalState,
            ExecutorActionFactory<ExecutorTaskInfo, TaskState, TaskInstanceSpec> actionFactory,
            DockerClient client) {
        super(initalState, new InstanceActionContext<>(executorId, instanceSpec, client), actionFactory, transitions);
    }
}