/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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

import lombok.Value;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 * Description of host directories to be mounted in containers
 */
@Value
public class MountedVolume {
    public enum MountMode {
        READ_WRITE,
        READ_ONLY
    }
    @NotEmpty(message = "- Provide mount path inside container")
    String pathInContainer;
    @NotEmpty(message = "- Provide host directory to mount")
    String pathOnHost;
    @NotNull(message = "- Specify whether mount is read-only or read-write")
    MountMode mode;
}
