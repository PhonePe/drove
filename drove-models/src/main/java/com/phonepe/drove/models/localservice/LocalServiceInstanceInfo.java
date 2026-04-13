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

package com.phonepe.drove.models.localservice;

import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfo;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfoVisitor;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import lombok.extern.jackson.Jacksonized;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Information about a specific instance of a local service.
 */
@Value
@Jacksonized
@Builder
@AllArgsConstructor
@With
@Schema(description = "Information about a specific instance of a local service running on an executor")
public class LocalServiceInstanceInfo implements DeployedInstanceInfo {
    @Schema(description = "Unique identifier of the local service", example = "MY_LOCAL_SERVICE")
    String serviceId;

    @Schema(description = "Name of the local service", example = "my-sidecar")
    String serviceName;

    @Schema(description = "Unique identifier of this instance", example = "LSI-00a1b2c3-0001")
    String instanceId;

    @Schema(description = "ID of the executor running this instance", example = "executor-1.example.com")
    String executorId;

    @Schema(description = "Local instance information including host and port details")
    LocalInstanceInfo localInfo;

    @Schema(description = "Resources allocated to this instance")
    List<ResourceAllocation> resources;

    @Schema(description = "Current state of the instance")
    LocalServiceInstanceState state;

    @Schema(description = "Custom metadata associated with this instance")
    Map<String, String> metadata;

    @Schema(description = "Error message if the instance is in an error state")
    String errorMessage;

    @Schema(description = "Timestamp when this instance was created")
    Date created;

    @Schema(description = "Timestamp when this instance was last updated")
    Date updated;

    @Override
    public <T> T accept(DeployedInstanceInfoVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String name() {
        return serviceName + "/" + instanceId;
    }

    @Override
    public String instanceId() {
        return instanceId;
    }
}
