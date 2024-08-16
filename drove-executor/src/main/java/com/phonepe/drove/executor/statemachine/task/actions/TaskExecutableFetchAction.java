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
import com.phonepe.drove.executor.dockerauth.DockerAuthConfig;
import com.phonepe.drove.executor.model.ExecutorTaskInfo;
import com.phonepe.drove.executor.statemachine.common.actions.CommonExecutableFetchAction;
import com.phonepe.drove.models.taskinstance.TaskState;

import javax.inject.Inject;

/**
 *
 */
public class TaskExecutableFetchAction extends CommonExecutableFetchAction<ExecutorTaskInfo, TaskState, TaskInstanceSpec> {

    @Inject
    public TaskExecutableFetchAction(DockerAuthConfig dockerAuthConfig) {
        super(dockerAuthConfig);
    }

    @Override
    protected TaskState defaultErrorState() {
        return TaskState.PROVISIONING_FAILED;
    }

    @Override
    protected TaskState stoppedState() {
        return TaskState.STOPPED;
    }

    @Override
    protected TaskState startState() {
        return TaskState.STARTING;
    }
}
