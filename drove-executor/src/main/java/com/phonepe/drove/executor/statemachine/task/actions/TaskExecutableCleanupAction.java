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
import com.phonepe.drove.executor.ExecutorOptions;
import com.phonepe.drove.executor.model.ExecutorTaskInfo;
import com.phonepe.drove.executor.statemachine.common.actions.CommonExecutableCleanupAction;
import com.phonepe.drove.models.taskinstance.TaskState;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

/**
 *
 */
@Slf4j
public class TaskExecutableCleanupAction extends CommonExecutableCleanupAction<ExecutorTaskInfo, TaskState, TaskInstanceSpec> {

    @Inject
    public TaskExecutableCleanupAction(ExecutorOptions options) {
       super(options);
    }

    @Override
    protected TaskState defaultErrorState() {
        return TaskState.STOPPED;
    }

    @Override
    protected TaskState stoppedState() {
        return TaskState.STOPPED;
    }

}
