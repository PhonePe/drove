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
public class DetailedDeviceSpec extends DeviceSpec {
    String driver;
    Integer count;
    List<String> deviceIds;
    List<List<String>> capabilities;
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
