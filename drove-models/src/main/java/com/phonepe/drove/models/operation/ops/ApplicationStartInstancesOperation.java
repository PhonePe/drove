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

package com.phonepe.drove.models.operation.ops;

import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ApplicationOperationType;
import com.phonepe.drove.models.operation.ApplicationOperationVisitor;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 * Operation to start additional instances of an existing application.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
@With
@Schema(description = "Operation to start additional instances of an existing application")
public class ApplicationStartInstancesOperation extends ApplicationOperation {
    @NotEmpty
    @Schema(description = "Unique identifier of the application", example = "MY_APP-1", requiredMode = Schema.RequiredMode.REQUIRED)
    String appId;

    @Min(0)
    @Max(1024)
    @Schema(description = "Number of additional instances to start (0-1024)", example = "5", minimum = "0", maximum = "1024")
    long instances;

    @NotNull
    @Valid
    @Schema(description = "Cluster operation specification with timeout and parallelism settings", requiredMode = Schema.RequiredMode.REQUIRED)
    ClusterOpSpec opSpec;

    public ApplicationStartInstancesOperation(String appId, long instances, ClusterOpSpec opSpec) {
        super(ApplicationOperationType.START_INSTANCES);
        this.appId = appId;
        this.instances = instances;
        this.opSpec = opSpec;
    }

    @Override
    public <T> T accept(ApplicationOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
