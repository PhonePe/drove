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
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotEmpty;

/**
 * Device is mapped directly. Equivalent to --device /dev/xx:/dev/yy:rw
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Schema(description = "Direct device mapping from host to container with optional permissions (equivalent to docker --device flag)")
public class DirectDeviceSpec extends DeviceSpec {

    @Getter
    @Schema(description = "Permission modes for device access")
    public enum DirectDevicePermissions {
        @Schema(description = "Read-only access")
        READ_ONLY("r"),
        @Schema(description = "Write-only access")
        WRITE_ONLY("w"),
        @Schema(description = "mknod access only")
        MKNOD_ONLY("m"),
        @Schema(description = "Full access (read, write, mknod)")
        ALL("rwm"),
        @Schema(description = "Read and write access")
        READ_WRITE("rw")
        ;

        private final String value;

        DirectDevicePermissions(String value) {
            this.value = value;
        }
    }

    @NotEmpty
    @Schema(description = "Path to the device on the host", example = "/dev/nvidia0", requiredMode = Schema.RequiredMode.REQUIRED)
    String pathOnHost;

    @Schema(description = "Path where device will be available inside the container", example = "/dev/nvidia0")
    String pathInContainer;

    @Schema(description = "Access permissions for the device")
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
