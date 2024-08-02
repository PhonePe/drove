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

package com.phonepe.drove.models.task;

import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.executable.ExecutableCoordinates;
import com.phonepe.drove.models.application.logging.LoggingSpec;
import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;
import com.phonepe.drove.models.config.ConfigSpec;
import com.phonepe.drove.models.interfaces.DeploymentSpec;
import com.phonepe.drove.models.interfaces.DeploymentSpecVisitor;
import lombok.Value;
import lombok.With;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;

/**
 *
 */
@Value
@With
public class TaskSpec implements DeploymentSpec {
    @NotEmpty(message = "- Source Application name is mandatory")
    @Pattern(regexp = "[a-zA-Z\\d\\-_]*", message = "- Only characters, numbers, hyphen and underscore is allowed")
    String sourceAppName;

    @NotEmpty(message = "- Task ID is mandatory")
    @Pattern(regexp = "[a-zA-Z\\d\\-_]*", message = "- Only characters, numbers, hyphen and underscore is allowed")
    String taskId;

    @NotNull(message = "- Executable details is required")
    @Valid
    ExecutableCoordinates executable;

    @Valid
    List<MountedVolume> volumes;

    @Valid
    List<ConfigSpec> configs;

    @Valid
    LoggingSpec logging;

    @NotEmpty(message = "- CPU/Memory requirements must be specified")
    @Valid
    List<ResourceRequirement> resources;

    @Valid
    PlacementPolicy placementPolicy;

    Map<String, String> tags;

    Map<String, String> env;

    @Override
    public <T> T accept(DeploymentSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
