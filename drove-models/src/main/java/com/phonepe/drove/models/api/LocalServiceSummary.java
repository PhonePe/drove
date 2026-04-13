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

package com.phonepe.drove.models.api;

import com.phonepe.drove.models.localservice.ActivationState;
import com.phonepe.drove.models.localservice.LocalServiceState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import java.util.Date;
import java.util.Map;

/**
 * Summary view of a local service
 */
@Value
@Schema(description = "Summary information about a local service")
public class LocalServiceSummary {
    @Schema(description = "Unique service identifier", example = "my-local-service-1234abcd")
    String id;

    @Schema(description = "Service name", example = "my-local-service")
    String name;

    @Schema(description = "Number of instances to run per host", example = "1")
    long instancesPerHost;

    @Schema(description = "Number of currently healthy instances", example = "10")
    long healthyInstances;

    @Schema(description = "Total CPU cores allocated across all instances", example = "10")
    long totalCPUs;

    @Schema(description = "Total memory in MB allocated across all instances", example = "5120")
    long totalMemory;

    @Schema(description = "Service tags for filtering and grouping")
    Map<String, String> tags;

    @Schema(description = "Whether the service is activated or deactivated")
    ActivationState activationState;

    @Schema(description = "Current service lifecycle state")
    LocalServiceState state;

    @Schema(description = "Timestamp when the service was created")
    Date created;

    @Schema(description = "Timestamp of the last service update")
    Date updated;
}
