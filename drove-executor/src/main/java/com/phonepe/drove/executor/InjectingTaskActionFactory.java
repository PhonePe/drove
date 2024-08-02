/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

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
