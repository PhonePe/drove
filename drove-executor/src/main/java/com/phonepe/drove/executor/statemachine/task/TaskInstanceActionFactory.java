package com.phonepe.drove.executor.statemachine.task;

import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.InstanceActionFactory;
import com.phonepe.drove.executor.model.ExecutorTaskInstanceInfo;
import com.phonepe.drove.models.taskinstance.TaskInstanceState;

/**
 *
 */
public interface TaskInstanceActionFactory extends InstanceActionFactory<ExecutorTaskInstanceInfo, TaskInstanceState, TaskInstanceSpec> {

}
