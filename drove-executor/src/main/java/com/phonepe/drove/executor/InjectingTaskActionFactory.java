package com.phonepe.drove.executor;

import com.google.inject.Injector;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorTaskInfo;
import com.phonepe.drove.executor.statemachine.ExecutorActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.task.TaskActionFactory;
import com.phonepe.drove.models.taskinstance.TaskState;
import com.phonepe.drove.statemachine.Transition;

/**
 *
 */
public class InjectingTaskActionFactory implements TaskActionFactory {
    private final Injector injector;

    public InjectingTaskActionFactory(Injector injector) {
        this.injector = injector;
    }

    @Override
    public ExecutorActionBase<ExecutorTaskInfo, TaskState, TaskInstanceSpec> create(Transition<ExecutorTaskInfo, Void, TaskState, InstanceActionContext<TaskInstanceSpec>, ExecutorActionBase<ExecutorTaskInfo, TaskState, TaskInstanceSpec>> transition) {
        return injector.getInstance(transition.getAction());
    }
}
