package com.phonepe.drove.executor;

import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.statemachine.ActionFactory;

/**
 *
 */
public interface InstanceActionFactory extends ActionFactory<ExecutorInstanceInfo, Void, InstanceState, InstanceActionContext, InstanceAction> {
}
