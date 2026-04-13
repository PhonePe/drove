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

import com.phonepe.drove.models.operation.LocalServiceOperation;
import com.phonepe.drove.models.operation.LocalServiceOperationType;
import com.phonepe.drove.models.operation.LocalServiceOperationVisitor;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.NotEmpty;

/**
 * Operation to update the number of instances per executor for a local service.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
@Schema(description = "Operation to update the number of instances per executor for a local service")
public class LocalServiceUpdateInstanceCountOperation extends LocalServiceOperation {
    @NotEmpty
    @Schema(description = "Unique identifier of the local service to update", example = "MY_LOCAL_SERVICE", requiredMode = Schema.RequiredMode.REQUIRED)
    String serviceId;

    @Range(min = 1, max = 256)
    @Schema(description = "New number of instances to run on each executor host (1-256)", example = "2", minimum = "1", maximum = "256")
    int instancesPerHost;

    public LocalServiceUpdateInstanceCountOperation(String serviceId, int instancesPerHost) {
        super(LocalServiceOperationType.UPDATE_INSTANCE_COUNT);
        this.serviceId = serviceId;
        this.instancesPerHost = instancesPerHost;
    }

    @Override
    public <T> T accept(LocalServiceOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
