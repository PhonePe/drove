package com.phonepe.drove.executor.statemachine.application;

import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.InstanceActionFactory;
import com.phonepe.drove.executor.model.ExecutorApplicationInstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;

/**
 *
 */
public abstract class ApplicationInstanceActionFactory implements InstanceActionFactory<ExecutorApplicationInstanceInfo, InstanceState, ApplicationInstanceSpec> {

}
