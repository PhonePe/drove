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

package com.phonepe.drove.models.operation.localserviceops;

import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.LocalServiceOperation;
import com.phonepe.drove.models.operation.LocalServiceOperationType;
import com.phonepe.drove.models.operation.LocalServiceOperationVisitor;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;

/**
 * Operation to restart a local service on all executor nodes.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
@Schema(description = "Operation to restart a local service on all executor nodes")
public class LocalServiceRestartOperation extends LocalServiceOperation {
    @NotEmpty
    @Schema(description = "Unique identifier of the local service to restart", example = "MY_LOCAL_SERVICE", requiredMode = Schema.RequiredMode.REQUIRED)
    String serviceId;

    @Schema(description = "If true, stops old instances before starting new ones. If false, starts new instances first (rolling restart)", example = "false", defaultValue = "false")
    boolean stopFirst;

    @Valid
    @Schema(description = "Cluster operation specification with timeout and parallelism settings")
    ClusterOpSpec clusterOpSpec;
    public LocalServiceRestartOperation(
            String serviceId,
            boolean stopFirst,
            ClusterOpSpec clusterOpSpec) {
        super(LocalServiceOperationType.RESTART);
        this.serviceId = serviceId;
        this.stopFirst = stopFirst;
        this.clusterOpSpec = clusterOpSpec;
    }

    @Override
    public <T> T accept(LocalServiceOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
