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

package com.phonepe.drove.models.application;

import com.phonepe.drove.models.application.checks.CheckModeSpec;
import io.dropwizard.util.Duration;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import javax.validation.Valid;
import java.util.List;

/**
 * Configuration for graceful shutdown of instances
 */
@Value
@Schema(description = "Configuration for graceful shutdown of instances before termination")
public class PreShutdownSpec {
    public static final PreShutdownSpec DEFAULT = new PreShutdownSpec(List.of(), Duration.seconds(0));

    @Valid
    @Schema(description = "List of hooks to execute before shutdown (e.g., deregistration from load balancer)")
    List<CheckModeSpec> hooks;

    @Schema(description = "Duration to wait after hooks complete before killing the container", example = "30 seconds")
    Duration waitBeforeKill;
}
