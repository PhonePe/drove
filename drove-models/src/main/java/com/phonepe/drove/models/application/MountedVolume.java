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

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 * Description of host directories to be mounted in containers
 */
@Value
@Schema(description = "Specification for mounting host directories into containers")
public class MountedVolume {
    @Schema(description = "Mount mode specifying read/write permissions for the volume")
    public enum MountMode {
        @Schema(description = "Volume is mounted with read and write permissions")
        READ_WRITE,
        @Schema(description = "Volume is mounted with read-only permissions")
        READ_ONLY
    }

    @NotEmpty(message = "- Provide mount path inside container")
    @Schema(description = "Path where the volume will be mounted inside the container",
            example = "/app/data", requiredMode = Schema.RequiredMode.REQUIRED)
    String pathInContainer;

    @NotEmpty(message = "- Provide host directory to mount")
    @Schema(description = "Path on the host machine to be mounted",
            example = "/mnt/data", requiredMode = Schema.RequiredMode.REQUIRED)
    String pathOnHost;

    @NotNull(message = "- Specify whether mount is read-only or read-write")
    @Schema(description = "Mount mode specifying read/write permissions", requiredMode = Schema.RequiredMode.REQUIRED)
    MountMode mode;
}
