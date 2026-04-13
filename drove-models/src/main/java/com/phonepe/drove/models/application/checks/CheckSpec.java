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

package com.phonepe.drove.models.application.checks;

import io.dropwizard.util.Duration;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * Configuration for health checks and readiness checks
 */
@Value
@Schema(description = "Configuration for health checks or readiness checks on application instances")
public class CheckSpec {

    @Valid
    @Schema(description = "The check mode specification defining how the check is performed")
    CheckModeSpec mode;

    @NotNull
    @Schema(description = "Maximum duration to wait for the check to complete", example = "5 seconds",
            requiredMode = Schema.RequiredMode.REQUIRED)
    Duration timeout;

    @NotNull
    @Schema(description = "Time interval between consecutive check attempts", example = "10 seconds",
            requiredMode = Schema.RequiredMode.REQUIRED)
    Duration interval;

    @Min(1)
    @Max(100)
    @Schema(description = "Number of consecutive check failures before marking instance as unhealthy",
            example = "3", minimum = "1", maximum = "100")
    int attempts;

    @Schema(description = "Initial delay before starting the first check after instance startup", example = "30 seconds")
    Duration initialDelay;
}
