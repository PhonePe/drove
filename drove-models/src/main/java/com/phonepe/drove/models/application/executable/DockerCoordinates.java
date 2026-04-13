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

package com.phonepe.drove.models.application.executable;

import io.dropwizard.util.Duration;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 * Docker container coordinates specifying the container image to deploy
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
@Schema(description = "Docker container coordinates specifying the container image to deploy")
public class DockerCoordinates extends ExecutableCoordinates {
    public static final Duration DEFAULT_PULL_TIMEOUT = Duration.minutes(15);

    @NotEmpty(message = "- Specify url for container")
    @Schema(description = "Docker image URL/reference to pull and deploy",
            example = "nginx:latest", requiredMode = Schema.RequiredMode.REQUIRED)
    String url;

    @NotNull(message = "- Specify container fetch (docker pull) timeout")
    @Schema(description = "Timeout for pulling the Docker image", example = "15 minutes",
            requiredMode = Schema.RequiredMode.REQUIRED)
    Duration dockerPullTimeout;

    public DockerCoordinates(String url, Duration dockerPullTimeout) {
        super(ExecutableType.DOCKER);
        this.url = url;
        this.dockerPullTimeout = dockerPullTimeout;
    }

    @Override
    public <T> T accept(ExecutableTypeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
