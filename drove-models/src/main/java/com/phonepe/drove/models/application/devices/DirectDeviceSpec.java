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

import lombok.*;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotEmpty;

/**
 * Device is mapped directly. Equivalent to --device /dev/xx/dev/yy:rw
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DirectDeviceSpec extends DeviceSpec {

    @Getter
    public enum DirectDevicePermissions {
        READ_ONLY("r"),
        WRITE_ONLY("w"),
        MKNOD_ONLY("m"),
        ALL("rwm"),
        READ_WRITE("rw")
        ;

        private final String value;

        DirectDevicePermissions(String value) {
            this.value = value;
        }
    }

    @NotEmpty
    String pathOnHost;

    String pathInContainer;

    DirectDevicePermissions permissions;

    @Builder
    @Jacksonized
    public DirectDeviceSpec(
            String pathOnHost,
            String pathInContainer,
            DirectDevicePermissions permissions) {
        super(DeviceSpecType.DIRECT);
        this.pathOnHost = pathOnHost;
        this.pathInContainer = pathInContainer;
        this.permissions = permissions;
    }

    @Override
    public <T> T accept(DeviceSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}