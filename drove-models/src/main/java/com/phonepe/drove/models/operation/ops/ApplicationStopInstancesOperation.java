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
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Operation to stop specific instances of an application.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
@With
@Schema(description = "Operation to stop specific instances of an application")
public class ApplicationStopInstancesOperation extends ApplicationOperation {
    @NotEmpty
    @Schema(description = "Unique identifier of the application", example = "MY_APP-1", requiredMode = Schema.RequiredMode.REQUIRED)
    String appId;

    @NotEmpty
    @Schema(description = "List of instance IDs to stop", example = "[\"AI-00a1b2c3-0001\", \"AI-00a1b2c3-0002\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    List<String> instanceIds;

    @Schema(description = "If true, stopped instances will not be respawned automatically", example = "false", defaultValue = "false")
    boolean skipRespawn;

    @NotNull
    @Valid
    @Schema(description = "Cluster operation specification with timeout and parallelism settings", requiredMode = Schema.RequiredMode.REQUIRED)
    ClusterOpSpec opSpec;

    public ApplicationStopInstancesOperation(
            String appId,
            List<String> instanceIds,
            boolean skipRespawn, ClusterOpSpec opSpec) {
        super(ApplicationOperationType.STOP_INSTANCES);
        this.appId = appId;
        this.instanceIds = instanceIds;
        this.skipRespawn = skipRespawn;
        this.opSpec = opSpec;
    }

    @Override
    public <T> T accept(ApplicationOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
