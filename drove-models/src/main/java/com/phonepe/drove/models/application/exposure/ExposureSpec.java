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

package com.phonepe.drove.models.application.exposure;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import javax.validation.constraints.NotEmpty;

/**
 * Specification for exposing application to load balancer
 */
@Value
@Schema(description = "Configuration for exposing application instances to a load balancer via virtual host")
public class ExposureSpec {
    @NotEmpty(message = "- Please provide virtual host name this app will be exposed as")
    @Schema(description = "Virtual host name for load balancer routing", example = "myapp.example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    String vhost;

    @NotEmpty(message = "- Name of the port that needs to be exposed")
    @Schema(description = "Name of the port (from portMappings) to expose", example = "http", requiredMode = Schema.RequiredMode.REQUIRED)
    String portName;

    @Schema(description = "Mode for instance exposure (ALL or ONE)", example = "ALL")
    ExposureMode mode;
}
