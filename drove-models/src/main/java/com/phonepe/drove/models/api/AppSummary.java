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

import com.phonepe.drove.models.application.ApplicationState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import java.util.Date;
import java.util.Map;

/**
 * Summary view of an application
 */
@Value
@Schema(description = "Summary information about a deployed application")
public class AppSummary {
    @Schema(description = "Unique application identifier (name-version hash)", example = "myapp-1234abcd")
    String id;

    @Schema(description = "Application name", example = "my-service")
    String name;

    @Schema(description = "Number of instances required by the deployment spec", example = "3")
    long requiredInstances;

    @Schema(description = "Number of currently healthy instances", example = "3")
    long healthyInstances;

    @Schema(description = "Total CPU cores allocated across all instances", example = "6")
    long totalCPUs;

    @Schema(description = "Total memory in MB allocated across all instances", example = "3072")
    long totalMemory;

    @Schema(description = "Application tags for filtering and grouping")
    Map<String, String> tags;

    @Schema(description = "Current application lifecycle state")
    ApplicationState state;

    @Schema(description = "Timestamp when the application was created")
    Date created;

    @Schema(description = "Timestamp of the last application update")
    Date updated;
}
