package com.phonepe.drove.executor.statemachine.application;


import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorApplicationInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionBase;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Slf4j
public abstract class ApplicationInstanceAction extends InstanceActionBase<ExecutorApplicationInstanceInfo, InstanceState, ApplicationInstanceSpec> {

    @Override
    protected InstanceState stoppedState() {
        return InstanceState.STOPPED;
    }
}
