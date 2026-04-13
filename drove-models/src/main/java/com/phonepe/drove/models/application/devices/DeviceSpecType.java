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

package com.phonepe.drove.models.application.devices;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Types of device mounts for containers
 */
@Schema(description = "Type of device mount specification for containers")
public enum DeviceSpecType {
    @Schema(description = "Direct device mapping with simple path and permissions")
    DIRECT,
    @Schema(description = "Detailed device configuration with drivers, capabilities, and options")
    DETAILED
}
