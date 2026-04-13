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

package com.phonepe.drove.models.instance;

import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfo;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfoVisitor;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Information about an application instance
 */
@Value
@Jacksonized
@Builder
@AllArgsConstructor
@Schema(description = "Detailed information about an application instance")
public class InstanceInfo implements DeployedInstanceInfo {
    @Schema(description = "Application ID", example = "my-service-1234abcd")
    String appId;

    @Schema(description = "Application name", example = "my-service")
    String appName;

    @Schema(description = "Unique instance identifier", example = "AI-my-service-1234abcd-001")
    String instanceId;

    @Schema(description = "ID of the executor running this instance", example = "executor-1-abcd1234")
    String executorId;

    @Schema(description = "Local networking information for the instance")
    LocalInstanceInfo localInfo;

    @Schema(description = "Resources allocated to this instance")
    List<ResourceAllocation> resources;

    @Schema(description = "Current instance lifecycle state")
    InstanceState state;

    @Schema(description = "Additional metadata key-value pairs")
    Map<String, String> metadata;

    @Schema(description = "Error message if instance is in an error state")
    String errorMessage;

    @Schema(description = "Timestamp when the instance was created")
    Date created;

    @Schema(description = "Timestamp of the last instance update")
    Date updated;

    @Override
    public <T> T accept(DeployedInstanceInfoVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String name() {
        return appName + "/" + instanceId;
    }

    @Override
    public String instanceId() {
        return instanceId;
    }
}
