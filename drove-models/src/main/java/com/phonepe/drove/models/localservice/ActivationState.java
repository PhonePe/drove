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

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Activation state of a local service.
 */
@Schema(description = "Activation state of a local service")
public enum ActivationState {
    @Schema(description = "Service is active and running instances")
    ACTIVE,
    @Schema(description = "Service is running in configuration testing mode")
    CONFIG_TESTING,
    @Schema(description = "Service is inactive and not running")
    INACTIVE
}
