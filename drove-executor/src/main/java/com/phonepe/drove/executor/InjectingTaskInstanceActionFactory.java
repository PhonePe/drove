package com.phonepe.drove.executor;

import com.google.inject.Injector;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorTaskInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.task.TaskInstanceActionFactory;
import com.phonepe.drove.models.taskinstance.TaskInstanceState;
import com.phonepe.drove.statemachine.Transition;

/**
 *
 */
public class InjectingTaskInstanceActionFactory extends TaskInstanceActionFactory {
    private final Injector injector;

    public InjectingTaskInstanceActionFactory(Injector injector) {
        this.injector = injector;
    }

    @Override
    public InstanceActionBase<ExecutorTaskInstanceInfo, TaskInstanceState, TaskInstanceSpec> create(Transition<ExecutorTaskInstanceInfo, Void, TaskInstanceState, InstanceActionContext<TaskInstanceSpec>, InstanceActionBase<ExecutorTaskInstanceInfo, TaskInstanceState, TaskInstanceSpec>> transition) {
        return injector.getInstance(transition.getAction());
    }
}
