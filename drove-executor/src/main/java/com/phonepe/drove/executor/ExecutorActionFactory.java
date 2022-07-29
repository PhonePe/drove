package com.phonepe.drove.executor;

import com.phonepe.drove.common.model.DeploymentUnitSpec;
import com.phonepe.drove.executor.model.DeployedExecutionObjectInfo;
import com.phonepe.drove.executor.statemachine.ExecutorActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.statemachine.ActionFactory;

/**
 *
 */
public interface ExecutorActionFactory<E extends DeployedExecutionObjectInfo, S extends Enum<S>, T extends DeploymentUnitSpec> extends ActionFactory<E, Void, S, InstanceActionContext<T>, ExecutorActionBase<E, S, T>> {
}
