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

package com.phonepe.drove.models.task;

import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.devices.DeviceSpec;
import com.phonepe.drove.models.application.executable.ExecutableCoordinates;
import com.phonepe.drove.models.application.logging.LoggingSpec;
import com.phonepe.drove.models.application.nonroot.UserSpec;
import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;
import com.phonepe.drove.models.config.ConfigSpec;
import com.phonepe.drove.models.interfaces.DeploymentSpec;
import com.phonepe.drove.models.interfaces.DeploymentSpecVisitor;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.With;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * Specification for running a one-time task on the Drove cluster.
 */
@Value
@With
@Schema(description = "Specification for running a one-time task on the Drove cluster. Tasks run to completion and then terminate.")
public class TaskSpec implements DeploymentSpec {
    @NotEmpty(message = "- Source Application name is mandatory")
    @Pattern(regexp = "[a-zA-Z\\d\\-_]*", message = "- Only characters, numbers, hyphen and underscore is allowed")
    @Schema(description = "Name of the source application that owns this task. Only alphanumeric characters, hyphens, and underscores are allowed", example = "my-batch-app", requiredMode = Schema.RequiredMode.REQUIRED, pattern = "[a-zA-Z\\d\\-_]*")
    String sourceAppName;

    @NotEmpty(message = "- Task ID is mandatory")
    @Pattern(regexp = "[a-zA-Z\\d\\-_]*", message = "- Only characters, numbers, hyphen and underscore is allowed")
    @Schema(description = "Unique identifier for the task. Only alphanumeric characters, hyphens, and underscores are allowed", example = "task-001", requiredMode = Schema.RequiredMode.REQUIRED, pattern = "[a-zA-Z\\d\\-_]*")
    String taskId;

    @NotNull(message = "- Executable details is required")
    @Valid
    @Schema(description = "Coordinates for the executable (Docker image or other executable type)", requiredMode = Schema.RequiredMode.REQUIRED)
    ExecutableCoordinates executable;

    @Valid
    @Schema(description = "List of volumes to mount into the container")
    List<MountedVolume> volumes;

    @Valid
    @Schema(description = "Configuration specifications for the task")
    List<ConfigSpec> configs;

    @Valid
    @Schema(description = "Logging configuration for the task")
    LoggingSpec logging;

    @NotEmpty(message = "- CPU/Memory requirements must be specified")
    @Valid
    @Schema(description = "Resource requirements (CPU, memory) for the task", requiredMode = Schema.RequiredMode.REQUIRED)
    List<ResourceRequirement> resources;

    @Valid
    @Schema(description = "Placement policy for scheduling the task on executor nodes")
    PlacementPolicy placementPolicy;

    @Schema(description = "Custom tags/labels for the task as key-value pairs", example = "{\"job-type\": \"etl\"}")
    Map<String, String> tags;

    @Schema(description = "Environment variables to set in the container as key-value pairs", example = "{\"INPUT_PATH\": \"/data/input\"}")
    Map<String, String> env;

    @Size(max = 2048)
    @Schema(description = "Command-line arguments to pass to the container entrypoint", maxLength = 2048)
    List<String> args;

    @Schema(description = "Device specifications for GPU or other hardware device access")
    List<DeviceSpec> devices;

    @Valid
    @Schema(description = "User specification for running containers as non-root user")
    UserSpec userSpec;

    @Override
    public <T> T accept(DeploymentSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
