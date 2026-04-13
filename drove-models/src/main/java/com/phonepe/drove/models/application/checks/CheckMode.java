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

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Available check modes for health/readiness checks
 */
@Schema(description = "Available check modes for health and readiness checks")
public enum CheckMode {
    @Schema(description = "HTTP-based check that calls an HTTP endpoint")
    HTTP,
    @Schema(description = "Command-based check that executes a command inside the container")
    CMD,
}
