package com.phonepe.drove.executor;

import com.phonepe.drove.common.ActionFactory;
import com.phonepe.drove.executor.statemachine.InstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;

/**
 *
 */
public interface InstanceActionFactory extends ActionFactory<InstanceInfo, InstanceState, InstanceActionContext, InstanceAction> {
}
