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

import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ApplicationOperationType;
import com.phonepe.drove.models.operation.ApplicationOperationVisitor;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.With;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * Operation to create a new application deployment
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
@With
@Schema(description = "Create a new application deployment")
public class ApplicationCreateOperation extends ApplicationOperation {
    @NotNull
    @Valid
    @Schema(description = "Application deployment specification", required = true)
    ApplicationSpec spec;

    @Min(0)
    @Max(2048)
    @Schema(description = "Initial number of instances to deploy", example = "3", minimum = "0", maximum = "2048")
    long instances;

    @NotNull
    @Valid
    @Schema(description = "Cluster operation parameters", required = true)
    ClusterOpSpec opSpec;

    public ApplicationCreateOperation(ApplicationSpec spec, long instances, ClusterOpSpec opSpec) {
        super(ApplicationOperationType.CREATE);
        this.spec = spec;
        this.instances = instances;
        this.opSpec = opSpec;
    }

    @Override
    public <T> T accept(ApplicationOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
