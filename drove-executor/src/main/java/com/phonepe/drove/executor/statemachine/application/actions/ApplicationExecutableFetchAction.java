package com.phonepe.drove.executor.statemachine.application.actions;

import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.dockerauth.DockerAuthConfig;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.common.actions.CommonExecutableFetchAction;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

/**
 *
 */
@Slf4j
public class ApplicationExecutableFetchAction extends CommonExecutableFetchAction<ExecutorInstanceInfo, InstanceState, ApplicationInstanceSpec> {

    @Inject
    public ApplicationExecutableFetchAction(DockerAuthConfig dockerAuthConfig) {
        super(dockerAuthConfig);
    }

    @Override
    protected InstanceState startState() {
        return InstanceState.STARTING;
    }

    @Override
    protected InstanceState defaultErrorState() {
        return InstanceState.PROVISIONING_FAILED;
    }

    @Override
    protected InstanceState stoppedState() {
        return InstanceState.STOPPED;
    }

    @Override
    public void stop() {
        //Nothing to do here
    }
}
