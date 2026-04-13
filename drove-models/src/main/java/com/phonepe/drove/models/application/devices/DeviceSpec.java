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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Base class for device loading specifications
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "DIRECT", value = DirectDeviceSpec.class),
        @JsonSubTypes.Type(name = "DETAILED", value = DetailedDeviceSpec.class),
})
@Schema(
    description = "Device specification for mounting host devices into containers. " +
                  "Supports direct device mapping or detailed configurations with drivers and capabilities.",
    discriminatorProperty = "type",
    discriminatorMapping = {
        @DiscriminatorMapping(value = "DIRECT", schema = DirectDeviceSpec.class),
        @DiscriminatorMapping(value = "DETAILED", schema = DetailedDeviceSpec.class)
    },
    subTypes = { DirectDeviceSpec.class, DetailedDeviceSpec.class }
)
public abstract class DeviceSpec {
    @Schema(description = "Type of device specification", requiredMode = Schema.RequiredMode.REQUIRED)
    private final DeviceSpecType type;

    public abstract <T> T accept(final DeviceSpecVisitor<T> visitor);
}
