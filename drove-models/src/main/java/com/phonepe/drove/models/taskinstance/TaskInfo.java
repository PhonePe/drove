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

package com.phonepe.drove.models.taskinstance;

import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.executable.ExecutableCoordinates;
import com.phonepe.drove.models.application.logging.LoggingSpec;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfo;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfoVisitor;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Information about a task instance
 */
@Value
@Schema(description = "Detailed information about a task instance")
public class TaskInfo implements DeployedInstanceInfo {
    @Schema(description = "Source application name that created this task", example = "batch-processor")
    String sourceAppName;

    @Schema(description = "Unique task identifier", example = "task-20231031-001")
    String taskId;

    @Schema(description = "Unique instance identifier", example = "TI-batch-processor-task-001")
    String instanceId;

    @Schema(description = "ID of the executor running this task", example = "executor-1-abcd1234")
    String executorId;

    @Schema(description = "Hostname of the executor", example = "executor-1.example.com")
    String hostname;

    @Schema(description = "Executable coordinates (Docker image, etc.)")
    ExecutableCoordinates executable;

    @Schema(description = "Resources allocated to this task")
    List<ResourceAllocation> resources;

    @Schema(description = "Volumes mounted in the task container")
    List<MountedVolume> volumes;

    @Schema(description = "Logging configuration")
    LoggingSpec loggingSpec;

    @Schema(description = "Environment variables for the task")
    Map<String, String> env;

    @Schema(description = "Current task lifecycle state")
    TaskState state;

    @Schema(description = "Additional metadata key-value pairs")
    Map<String, String> metadata;

    @Schema(description = "Task execution result (if completed)")
    TaskResult taskResult;

    @Schema(description = "Error message if task failed")
    String errorMessage;

    @Schema(description = "Timestamp when the task was created")
    Date created;

    @Schema(description = "Timestamp of the last task update")
    Date updated;

    @Override
    public <T> T accept(DeployedInstanceInfoVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String name() {
        return sourceAppName + "/" + instanceId;
    }

    @Override
    public String instanceId() {
        return instanceId;
    }
}
