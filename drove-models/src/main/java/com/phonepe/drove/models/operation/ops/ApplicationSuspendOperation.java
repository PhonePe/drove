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
import javax.validation.constraints.NotNull;

/**
 * Operation to suspend an application, stopping all instances but keeping the application definition.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
@With
@Schema(description = "Operation to suspend an application, stopping all instances but keeping the application definition")
public class ApplicationSuspendOperation extends ApplicationOperation {
    @NotNull
    @Valid
    @Schema(description = "Unique identifier of the application to suspend", example = "MY_APP-1", requiredMode = Schema.RequiredMode.REQUIRED)
    String appId;

    @NotNull
    @Valid
    @Schema(description = "Cluster operation specification with timeout and parallelism settings", requiredMode = Schema.RequiredMode.REQUIRED)
    ClusterOpSpec opSpec;

    @Builder
    public ApplicationSuspendOperation(String appId, ClusterOpSpec opSpec) {
        super(ApplicationOperationType.SUSPEND);
        this.appId = appId;
        this.opSpec = opSpec;
    }

    @Override
    public <T> T accept(ApplicationOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
