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
import com.phonepe.drove.models.task.TaskSpec;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class TaskCreateOperation extends TaskOperation {

    @NotNull
    @Valid
    TaskSpec spec;

    @NotNull
    @Valid
    ClusterOpSpec opSpec;

    public TaskCreateOperation(TaskSpec spec, ClusterOpSpec opSpec) {
        super(TaskOperationType.CREATE);
        this.spec = spec;
        this.opSpec = opSpec;
    }

    @Override
    public <T> T accept(final TaskOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
