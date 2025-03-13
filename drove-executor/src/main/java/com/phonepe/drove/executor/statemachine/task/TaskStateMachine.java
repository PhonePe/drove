/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.executor.statemachine.task;

import com.github.dockerjava.api.DockerClient;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.ExecutorActionFactory;
import com.phonepe.drove.executor.model.ExecutorTaskInfo;
import com.phonepe.drove.executor.statemachine.ExecutorActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.task.actions.*;
import com.phonepe.drove.models.taskinstance.TaskState;
import com.phonepe.drove.statemachine.StateData;
import com.phonepe.drove.statemachine.StateMachine;
import com.phonepe.drove.statemachine.Transition;
import lombok.NonNull;

import java.util.List;

import static com.phonepe.drove.models.taskinstance.TaskState.*;


/**
 *
 */
public class TaskStateMachine extends StateMachine<ExecutorTaskInfo, Void, TaskState,
        InstanceActionContext<TaskInstanceSpec>, ExecutorActionBase<ExecutorTaskInfo, TaskState,
                TaskInstanceSpec>> {
    private static final List<Transition<ExecutorTaskInfo, Void, TaskState,
            InstanceActionContext<TaskInstanceSpec>, ExecutorActionBase<ExecutorTaskInfo, TaskState,
                        TaskInstanceSpec>>> transitions
            = List.of(
            new Transition<>(PENDING,
                             TaskSpecValidator.class,
                             PROVISIONING,
                             STOPPED),
            new Transition<>(PROVISIONING,
                             TaskExecutableFetchAction.class,
                             STARTING,
                             PROVISIONING_FAILED),
            new Transition<>(STARTING,
                             TaskRunAction.class,
                             RUNNING,
                             RUN_FAILED,
                             RUN_COMPLETED),
            new Transition<>(RUNNING,
                             TaskMonitoringAction.class,
                             RUN_COMPLETED),
            new Transition<>(RUN_COMPLETED,
                             TaskDestroyAction.class,
                             DEPROVISIONING),
            new Transition<>(RUN_FAILED,
                             TaskDestroyAction.class,
                             DEPROVISIONING),
            new Transition<>(PROVISIONING_FAILED,
                             TaskDestroyAction.class,
                             DEPROVISIONING),
            new Transition<>(DEPROVISIONING,
                             TaskExecutableCleanupAction.class,
                             STOPPED),
            new Transition<>(UNKNOWN,
                             TaskRecoveryAction.class,
                             RUNNING,
                             STOPPED)
                     );

    public TaskStateMachine(
            String executorId,
            TaskInstanceSpec instanceSpec,
            @NonNull StateData<TaskState, ExecutorTaskInfo> initalState,
            ExecutorActionFactory<ExecutorTaskInfo, TaskState, TaskInstanceSpec> actionFactory,
            DockerClient client) {
        super(initalState, new InstanceActionContext<>(executorId, instanceSpec, client, false), actionFactory, transitions);
    }
}
