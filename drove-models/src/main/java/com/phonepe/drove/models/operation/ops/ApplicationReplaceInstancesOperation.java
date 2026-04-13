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
import java.util.Collections;
import java.util.Set;

/**
 * Operation to replace specific instances of an application with new ones.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
@With
@Schema(description = "Operation to replace specific instances of an application with new ones")
public class ApplicationReplaceInstancesOperation extends ApplicationOperation {
    @NotEmpty
    @Schema(description = "Unique identifier of the application", example = "MY_APP-1", requiredMode = Schema.RequiredMode.REQUIRED)
    String appId;

    @Schema(description = "Set of instance IDs to replace. If empty, all instances will be replaced", example = "[\"AI-00a1b2c3-0001\", \"AI-00a1b2c3-0002\"]")
    Set<String> instanceIds;

    @Schema(description = "If true, old instances are stopped before new ones are started. If false, new instances are started first (rolling replacement)", example = "false", defaultValue = "false")
    boolean stopFirst;

    @NotNull
    @Valid
    @Schema(description = "Cluster operation specification with timeout and parallelism settings", requiredMode = Schema.RequiredMode.REQUIRED)
    ClusterOpSpec opSpec;

    public ApplicationReplaceInstancesOperation(String appId, Set<String> instanceIds,
                                                boolean stopFirst,
                                                ClusterOpSpec opSpec) {
        super(ApplicationOperationType.REPLACE_INSTANCES);
        this.appId = appId;
        this.instanceIds = instanceIds == null ? Collections.emptySet() : instanceIds;
        this.stopFirst = stopFirst;
        this.opSpec = opSpec;
    }

    @Override
    public <T> T accept(ApplicationOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
