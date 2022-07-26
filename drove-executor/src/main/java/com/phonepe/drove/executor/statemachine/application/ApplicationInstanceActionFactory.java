package com.phonepe.drove.executor.statemachine.application;

import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.ExecutorActionFactory;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;

/**
 *
 */
public interface ApplicationInstanceActionFactory extends ExecutorActionFactory<ExecutorInstanceInfo, InstanceState, ApplicationInstanceSpec> {

}
