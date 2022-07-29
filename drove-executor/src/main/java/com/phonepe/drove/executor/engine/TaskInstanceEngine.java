package com.phonepe.drove.executor.engine;

import com.github.dockerjava.api.DockerClient;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.ExecutorActionFactory;
import com.phonepe.drove.executor.managed.ExecutorIdManager;
import com.phonepe.drove.executor.model.ExecutorTaskInfo;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.statemachine.ExecutorActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.task.TaskStateMachine;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskState;
import com.phonepe.drove.statemachine.StateData;
import com.phonepe.drove.statemachine.StateMachine;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static com.phonepe.drove.models.taskinstance.TaskState.LOST;
import static com.phonepe.drove.models.taskinstance.TaskState.RUNNING;


/**
 *
 */
@Slf4j
public class TaskInstanceEngine extends InstanceEngine<ExecutorTaskInfo, TaskState, TaskInstanceSpec, TaskInfo> {


    public TaskInstanceEngine(
            final ExecutorIdManager executorIdManager, ExecutorService service,
            ExecutorActionFactory<ExecutorTaskInfo, TaskState, TaskInstanceSpec> actionFactory,
            ResourceManager resourceDB, DockerClient client) {
        super(executorIdManager, service, actionFactory, resourceDB, client);
    }

    @Override
    protected StateData<TaskState, ExecutorTaskInfo> createInitialState(
            TaskInstanceSpec spec,
            Date currDate,
            ExecutorIdManager executorIdManager) {
        return StateData.create(TaskState.PENDING,
                                new ExecutorTaskInfo(spec.getTaskId(),
                                                     spec.getSourceAppName(),
                                                     spec.getInstanceId(),
                                                     executorIdManager.executorId().orElse(null),
                                                     null,
                                                     spec.getExecutable(),
                                                     spec.getResources(),
                                                     spec.getVolumes(),
                                                     spec.getLoggingSpec(),
                                                     spec.getEnv(),
                                                     Map.of(),
                                                     currDate,
                                                     currDate));
    }

    @Override
    protected TaskState lostState() {
        return LOST;
    }

    @Override
    protected boolean isTerminal(TaskState state) {
        return state.isTerminal();
    }

    @Override
    protected boolean isError(TaskState state) {
        return state.isError();
    }

    @Override
    protected boolean isRunning(TaskState state) {
        return RUNNING.equals(state);
    }

    @Override
    protected StateMachine<ExecutorTaskInfo, Void, TaskState, InstanceActionContext<TaskInstanceSpec>,
            ExecutorActionBase<ExecutorTaskInfo, TaskState, TaskInstanceSpec>> createStateMachine(
            String executorId,
            TaskInstanceSpec spec,
            StateData<TaskState, ExecutorTaskInfo> currentState,
            ExecutorActionFactory<ExecutorTaskInfo, TaskState, TaskInstanceSpec> actionFactory,
            DockerClient client) {
        return new TaskStateMachine(executorId,
                                    spec,
                                    currentState,
                                    actionFactory,
                                    client);
    }

    @Override
    protected TaskInfo convertStateToInstanceInfo(StateData<TaskState, ExecutorTaskInfo> currentState) {
        return ExecutorUtils.convertToTaskInfo(currentState);
    }
}
