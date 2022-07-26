package com.phonepe.drove.executor.statemachine.task.actions;

import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.dockerauth.DockerAuthConfig;
import com.phonepe.drove.executor.model.ExecutorTaskInfo;
import com.phonepe.drove.executor.statemachine.common.actions.CommonExecutableFetchAction;
import com.phonepe.drove.models.taskinstance.TaskState;

import javax.inject.Inject;

/**
 *
 */
public class TaskExecutableFetchAction extends CommonExecutableFetchAction<ExecutorTaskInfo, TaskState, TaskInstanceSpec> {

    @Inject
    public TaskExecutableFetchAction(DockerAuthConfig dockerAuthConfig) {
        super(dockerAuthConfig);
    }

    @Override
    protected TaskState defaultErrorState() {
        return TaskState.PROVISIONING_FAILED;
    }

    @Override
    protected TaskState stoppedState() {
        return TaskState.STOPPED;
    }

    @Override
    protected TaskState startState() {
        return TaskState.STARTING;
    }
}
