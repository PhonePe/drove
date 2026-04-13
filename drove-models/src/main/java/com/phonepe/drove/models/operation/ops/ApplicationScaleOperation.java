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
import javax.validation.constraints.NotNull;

/**
 * Operation to scale application instances
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
@With
@Schema(description = "Scale application to a specific number of instances")
public class ApplicationScaleOperation extends ApplicationOperation {
    @NotNull
    @Valid
    @Schema(description = "Application ID to scale", example = "my-service-1234abcd", required = true)
    String appId;

    @Min(0)
    @Max(2048)
    @Schema(description = "Desired number of instances", example = "5", minimum = "0", maximum = "2048")
    long requiredInstances;

    @NotNull
    @Valid
    @Schema(description = "Cluster operation parameters", required = true)
    ClusterOpSpec opSpec;

    public ApplicationScaleOperation(String appId, long requiredInstances, ClusterOpSpec opSpec) {
        super(ApplicationOperationType.SCALE_INSTANCES);
        this.appId = appId;
        this.requiredInstances = requiredInstances;
        this.opSpec = opSpec;
    }

    @Override
    public <T> T accept(ApplicationOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
