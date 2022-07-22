package com.phonepe.drove.executor.engine;

import com.github.dockerjava.api.DockerClient;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.InstanceActionFactory;
import com.phonepe.drove.executor.managed.ExecutorIdManager;
import com.phonepe.drove.executor.model.ExecutorTaskInstanceInfo;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.statemachine.InstanceActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.task.TaskInstanceStateMachine;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import com.phonepe.drove.models.taskinstance.TaskInstanceInfo;
import com.phonepe.drove.models.taskinstance.TaskInstanceState;
import com.phonepe.drove.statemachine.StateData;
import com.phonepe.drove.statemachine.StateMachine;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Date;
import java.util.concurrent.ExecutorService;

import static com.phonepe.drove.models.taskinstance.TaskInstanceState.LOST;
import static com.phonepe.drove.models.taskinstance.TaskInstanceState.RUNNING;


/**
 *
 */
@Slf4j
public class TaskInstanceEngine extends InstanceEngine<ExecutorTaskInstanceInfo, TaskInstanceState, TaskInstanceSpec, TaskInstanceInfo> {


    public TaskInstanceEngine(
            final ExecutorIdManager executorIdManager, ExecutorService service,
            InstanceActionFactory<ExecutorTaskInstanceInfo, TaskInstanceState, TaskInstanceSpec> actionFactory,
            ResourceManager resourceDB, DockerClient client) {
        super(executorIdManager, service, actionFactory, resourceDB, client);
    }

    @Override
    protected StateData<TaskInstanceState, ExecutorTaskInstanceInfo> createInitialState(
            TaskInstanceSpec spec,
            Date currDate,
            ExecutorIdManager executorIdManager) {
        return StateData.create(TaskInstanceState.PENDING,
                                new ExecutorTaskInstanceInfo(spec.getTaskId(),
                                                         spec.getTaskName(),
                                                         spec.getInstanceId(),
                                                         executorIdManager.executorId().orElse(null),
                                                         null,
                                                         spec.getResources(),
                                                         Collections.emptyMap(),
                                                         currDate,
                                                         currDate));
    }

    @Override
    protected TaskInstanceState lostState() {
        return LOST;
    }

    @Override
    protected boolean isTerminal(TaskInstanceState state) {
        return state.isTerminal();
    }

    @Override
    protected boolean isError(TaskInstanceState state) {
        return state.isError();
    }

    @Override
    protected boolean isRunning(TaskInstanceState state) {
        return RUNNING.equals(state);
    }

    @Override
    protected StateMachine<ExecutorTaskInstanceInfo, Void, TaskInstanceState, InstanceActionContext<TaskInstanceSpec>,
            InstanceActionBase<ExecutorTaskInstanceInfo, TaskInstanceState, TaskInstanceSpec>> createStateMachine(
            String executorId,
            TaskInstanceSpec spec,
            StateData<TaskInstanceState, ExecutorTaskInstanceInfo> currentState,
            InstanceActionFactory<ExecutorTaskInstanceInfo, TaskInstanceState, TaskInstanceSpec> actionFactory,
            DockerClient client) {
        return new TaskInstanceStateMachine(executorId,
                                            spec,
                                            currentState,
                                            actionFactory,
                                            client);
    }

    @Override
    protected TaskInstanceInfo convertStateToInstanceInfo(StateData<TaskInstanceState, ExecutorTaskInstanceInfo> currentState) {
        return ExecutorUtils.convertToTaskInfo(currentState);
    }
}
