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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import javax.validation.constraints.*;

/**
 * Specification for a port exposed by the container
 */
@Value
@Schema(description = "Specification for a port exposed by the container")
public class PortSpec {
    @NotEmpty(message = "- Specify a human readable name for the port." +
            " This will be used as key in health-check/readiness checks etc")
    @Pattern(regexp = "[a-zA-Z0-9\\-_]*", message = "- Only characters, numbers, hyphen and underscore is allowed")
    @Schema(description = "Human readable name for the port, used as key in health-check/readiness checks",
            example = "main", requiredMode = Schema.RequiredMode.REQUIRED)
    String name;

    @Min(value = 1, message = "- Port cannot be negative or 0")
    @Max(value = 65_535, message = "- Port cannot be more than 65K")
    @Schema(description = "Port number inside the container (1-65535)", example = "8080",
            minimum = "1", maximum = "65535", requiredMode = Schema.RequiredMode.REQUIRED)
    int port;

    @NotNull(message = "- Please specify port type [HTTP/TCP/UDP]")
    @Schema(description = "Protocol type for the port", requiredMode = Schema.RequiredMode.REQUIRED)
    PortType type;
}
