package com.phonepe.drove.executor.statemachine.task;

import com.github.dockerjava.api.DockerClient;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.InstanceActionFactory;
import com.phonepe.drove.executor.model.ExecutorTaskInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.task.actions.TaskInstanceSpecValidator;
import com.phonepe.drove.models.taskinstance.TaskInstanceState;
import com.phonepe.drove.statemachine.StateData;
import com.phonepe.drove.statemachine.StateMachine;
import com.phonepe.drove.statemachine.Transition;
import lombok.NonNull;

import java.util.List;

import static com.phonepe.drove.models.taskinstance.TaskInstanceState.*;


/**
 *
 */
public class TaskInstanceStateMachine extends StateMachine<ExecutorTaskInstanceInfo, Void, TaskInstanceState, InstanceActionContext<TaskInstanceSpec>, InstanceActionBase<ExecutorTaskInstanceInfo, TaskInstanceState, TaskInstanceSpec>> {
    private static final List<Transition<ExecutorTaskInstanceInfo, Void, TaskInstanceState, InstanceActionContext<TaskInstanceSpec>, InstanceActionBase<ExecutorTaskInstanceInfo, TaskInstanceState, TaskInstanceSpec>>> transitions
            = List.of(
            new Transition<>(PENDING,
                             TaskInstanceSpecValidator.class,
                             PROVISIONING,
                             STOPPING),
            new Transition<>(PROVISIONING,
                             TaskExecutableFetchAction.class,
                             STARTING,
                             PROVISIONING_FAILED)
            );

    public TaskInstanceStateMachine(
            String executorId,
            TaskInstanceSpec instanceSpec,
            @NonNull StateData<TaskInstanceState, ExecutorTaskInstanceInfo> initalState,
            InstanceActionFactory<ExecutorTaskInstanceInfo, TaskInstanceState, TaskInstanceSpec> actionFactory, DockerClient client) {
        super(initalState, new InstanceActionContext<>(executorId, instanceSpec, client), actionFactory, transitions);
    }
}
