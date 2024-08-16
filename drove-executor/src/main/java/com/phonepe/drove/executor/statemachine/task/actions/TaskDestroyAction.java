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

package com.phonepe.drove.executor.statemachine.task.actions;

import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorTaskInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.common.actions.CommonContainerCleanupAction;
import com.phonepe.drove.models.taskinstance.TaskState;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Slf4j
public class TaskDestroyAction extends CommonContainerCleanupAction<ExecutorTaskInfo, TaskState, TaskInstanceSpec> {

    @Override
    protected StateData<TaskState, ExecutorTaskInfo> preRemoveAction(
            InstanceActionContext<TaskInstanceSpec> context,
            StateData<TaskState, ExecutorTaskInfo> currentState) {
        return StateData.from(currentState, TaskState.DEPROVISIONING);
    }

    @Override
    protected TaskState defaultErrorState() {
        return TaskState.DEPROVISIONING;
    }

    @Override
    protected TaskState stoppedState() {
        return defaultErrorState();
    }

    @Override
    public void stop() {
        //Nothing to do here. This job is not stoppable
    }


}
