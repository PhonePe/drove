package com.phonepe.drove.executor.statemachine.task;

import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.dockerauth.DockerAuthConfig;
import com.phonepe.drove.executor.model.ExecutorTaskInstanceInfo;
import com.phonepe.drove.executor.statemachine.common.actions.CommonExecutableFetchAction;
import com.phonepe.drove.models.taskinstance.TaskInstanceState;

import javax.inject.Inject;

/**
 *
 */
public class TaskExecutableFetchAction extends CommonExecutableFetchAction<ExecutorTaskInstanceInfo, TaskInstanceState, TaskInstanceSpec> {

    @Inject
    public TaskExecutableFetchAction(DockerAuthConfig dockerAuthConfig) {
        super(dockerAuthConfig);
    }

    @Override
    protected TaskInstanceState defaultErrorState() {
        return TaskInstanceState.PROVISIONING_FAILED;
    }

    @Override
    protected TaskInstanceState stoppedState() {
        return TaskInstanceState.STOPPED;
    }

    @Override
    protected TaskInstanceState startState() {
        return TaskInstanceState.STARTING;
    }
}
