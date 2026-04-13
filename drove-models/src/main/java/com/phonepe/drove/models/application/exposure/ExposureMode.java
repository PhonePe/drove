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

/**
 * Mode for exposing application instances to load balancer
 */
@Schema(description = "Mode for exposing application instances to the load balancer")
public enum ExposureMode {
    @Schema(description = "Expose all healthy instances to the load balancer")
    ALL,
    @Schema(description = "Expose only one instance (useful for active-passive setups)")
    ONE
}
