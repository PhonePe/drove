package com.phonepe.drove.executor;

import com.phonepe.drove.common.model.DeploymentUnitSpec;
import com.phonepe.drove.executor.model.DeployedExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.statemachine.ActionFactory;

/**
 *
 */
public interface InstanceActionFactory<E extends DeployedExecutorInstanceInfo, S extends Enum<S>, T extends DeploymentUnitSpec> extends ActionFactory<E, Void, S, InstanceActionContext<T>, InstanceActionBase<E, S, T>> {
}
