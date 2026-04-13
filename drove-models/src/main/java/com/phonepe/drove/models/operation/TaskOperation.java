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

package com.phonepe.drove.models.operation;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.phonepe.drove.models.operation.taskops.TaskCreateOperation;
import com.phonepe.drove.models.operation.taskops.TaskKillOperation;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Base class for task operations. Tasks are one-time execution units that run to completion.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "CREATE", value = TaskCreateOperation.class),
        @JsonSubTypes.Type(name = "KILL", value = TaskKillOperation.class)
})
@Data
@Schema(
        description = "Base class for task operations. Tasks are one-time execution units that run to completion.",
        discriminatorProperty = "type",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "CREATE", schema = TaskCreateOperation.class),
                @DiscriminatorMapping(value = "KILL", schema = TaskKillOperation.class)
        },
        subTypes = {
                TaskCreateOperation.class,
                TaskKillOperation.class
        }
)
public abstract class TaskOperation {
    @Schema(description = "Type of task operation", requiredMode = Schema.RequiredMode.REQUIRED)
    private final TaskOperationType type;

    protected TaskOperation(TaskOperationType type) {
        this.type = type;
    }

    public abstract <T> T accept(final TaskOperationVisitor<T> visitor);
}
