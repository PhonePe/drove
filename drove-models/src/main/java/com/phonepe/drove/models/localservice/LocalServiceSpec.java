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

import com.phonepe.drove.models.application.JobType;
import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.PortSpec;
import com.phonepe.drove.models.application.PreShutdownSpec;
import com.phonepe.drove.models.application.checks.CheckSpec;
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
 * Specification for deploying a local service that runs on all executor nodes in the cluster.
 */
@Value
@With
@Schema(description = "Specification for deploying a local service that runs on all executor nodes in the cluster")
public class LocalServiceSpec implements DeploymentSpec {
    @NotEmpty(message = "- Service name is mandatory")
    @Pattern(regexp = "[a-zA-Z\\d\\-_]*", message = "- Only characters, numbers, hyphen and underscore is allowed")
    @Schema(description = "Unique name of the local service. Only alphanumeric characters, hyphens, and underscores are allowed", example = "my-sidecar-service", requiredMode = Schema.RequiredMode.REQUIRED, pattern = "[a-zA-Z\\d\\-_]*")
    String name;

    @NotEmpty(message = "- Service version is mandatory")
    @Pattern(regexp = "[a-zA-Z\\d\\-_]*", message = "- Only characters, numbers, hyphen and underscore is allowed")
    @Schema(description = "Version identifier for the service. Only alphanumeric characters, hyphens, and underscores are allowed", example = "v1-0-0", requiredMode = Schema.RequiredMode.REQUIRED, pattern = "[a-zA-Z\\d\\-_]*")
    String version;

    @NotNull(message = "- Executable details is required")
    @Valid
    @Schema(description = "Coordinates for the executable (Docker image or other executable type)", requiredMode = Schema.RequiredMode.REQUIRED)
    ExecutableCoordinates executable;

    @NotEmpty(message = "- Port specifications are needed")
    @Valid
    @Schema(description = "List of ports to expose from the container", requiredMode = Schema.RequiredMode.REQUIRED)
    List<PortSpec> exposedPorts;

    @Valid
    @Schema(description = "List of volumes to mount into the container")
    List<MountedVolume> volumes;

    @Valid
    @Schema(description = "Configuration specifications for the service")
    List<ConfigSpec> configs;

    @NotNull(message = "- Specify if job is a computation or a service")
    @Schema(description = "Type of job - SERVICE for long-running services, COMPUTATION for batch jobs", requiredMode = Schema.RequiredMode.REQUIRED)
    JobType type;

    @Valid
    @Schema(description = "Logging configuration for the service")
    LoggingSpec logging;

    @NotEmpty(message = "- CPU/Memory requirements must be specified")
    @Valid
    @Schema(description = "Resource requirements (CPU, memory) for each instance", requiredMode = Schema.RequiredMode.REQUIRED)
    List<ResourceRequirement> resources;

    @Valid
    @Schema(description = "Placement policy for scheduling instances on executor nodes")
    PlacementPolicy placementPolicy;

    @NotNull
    @Valid
    @Schema(description = "Health check specification to determine instance health", requiredMode = Schema.RequiredMode.REQUIRED)
    CheckSpec healthcheck;

    @NotNull
    @Valid
    @Schema(description = "Readiness check specification to determine when instance is ready", requiredMode = Schema.RequiredMode.REQUIRED)
    CheckSpec readiness;

    @Schema(description = "Custom tags/labels for the service as key-value pairs", example = "{\"team\": \"platform\"}")
    Map<String, String> tags;

    @Schema(description = "Environment variables to set in the container as key-value pairs", example = "{\"LOG_LEVEL\": \"INFO\"}")
    Map<String, String> env;

    @Size(max = 2048)
    @Schema(description = "Command-line arguments to pass to the container entrypoint", maxLength = 2048)
    List<String> args;

    @Schema(description = "Device specifications for GPU or other hardware device access")
    List<DeviceSpec> devices;

    @Valid
    @Schema(description = "Pre-shutdown hooks to execute before stopping an instance")
    PreShutdownSpec preShutdown;

    @Valid
    @Schema(description = "User specification for running containers as non-root user")
    UserSpec userSpec;

    @Override
    public <T> T accept(DeploymentSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
