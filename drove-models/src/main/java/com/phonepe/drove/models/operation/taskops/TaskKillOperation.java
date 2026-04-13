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

package com.phonepe.drove.models.operation.taskops;

import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.TaskOperation;
import com.phonepe.drove.models.operation.TaskOperationType;
import com.phonepe.drove.models.operation.TaskOperationVisitor;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 * Operation to kill (terminate) a running task.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
@Schema(description = "Operation to kill (terminate) a running task")
public class TaskKillOperation extends TaskOperation {

    @NotEmpty
    @Schema(description = "Name of the source application that owns the task", example = "MY_APP", requiredMode = Schema.RequiredMode.REQUIRED)
    String sourceAppName;

    @NotEmpty
    @Schema(description = "Unique identifier of the task to kill", example = "T00001", requiredMode = Schema.RequiredMode.REQUIRED)
    String taskId;

    @NotNull
    @Valid
    @Schema(description = "Cluster operation specification with timeout settings", requiredMode = Schema.RequiredMode.REQUIRED)
    ClusterOpSpec opSpec;

    public TaskKillOperation(String sourceAppName, String taskId, ClusterOpSpec opSpec) {
        super(TaskOperationType.KILL);
        this.sourceAppName = sourceAppName;
        this.taskId = taskId;
        this.opSpec = opSpec;
    }

    @Override
    public <T> T accept(TaskOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
