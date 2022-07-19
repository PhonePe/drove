package com.phonepe.drove.executor.statemachine.application.actions;

import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.ExecutorOptions;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.common.actions.CommonExecutableCleanupAction;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

/**
 *
 */
@Slf4j
public class ApplicationExecutableCleanupAction extends CommonExecutableCleanupAction<ExecutorInstanceInfo, InstanceState, ApplicationInstanceSpec> {

    @Inject
    public ApplicationExecutableCleanupAction(ExecutorOptions options) {
       super(options);
    }

    @Override
    protected InstanceState defaultErrorState() {
        return InstanceState.STOPPED;
    }

    @Override
    protected InstanceState stoppedState() {
        return InstanceState.STOPPED;
    }

}
