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
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.Map;

/**
 * Can be used to pass complicated options like drivers, capabilities etc
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Schema(description = "Detailed device configuration for complex device setups with drivers, capabilities, and options (e.g., GPU devices)")
public class DetailedDeviceSpec extends DeviceSpec {
    @Schema(description = "Device driver name (e.g., 'nvidia')", example = "nvidia")
    String driver;

    @Schema(description = "Number of devices to allocate", example = "1")
    Integer count;

    @Schema(description = "Specific device IDs to use", example = "[\"GPU-abc123\"]")
    List<String> deviceIds;

    @Schema(description = "Device capabilities required (e.g., [['gpu'], ['nvidia', 'compute']])")
    List<List<String>> capabilities;

    @Schema(description = "Additional driver options as key-value pairs")
    Map<String, String> options;

    @Jacksonized
    @Builder
    public DetailedDeviceSpec(
            String driver,
            Integer count,
            List<String> deviceIds,
            List<List<String>> capabilities,
            Map<String, String> options) {
        super(DeviceSpecType.DETAILED);
        this.driver = driver;
        this.count = count;
        this.deviceIds = deviceIds;
        this.capabilities = capabilities;
        this.options = options;
    }

    @Override
    public <T> T accept(DeviceSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
