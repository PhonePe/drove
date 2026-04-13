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

package com.phonepe.drove.models.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.phonepe.drove.models.config.impl.ControllerHttpFetchConfigSpec;
import com.phonepe.drove.models.config.impl.ExecutorHttpFetchConfigSpec;
import com.phonepe.drove.models.config.impl.ExecutorLocalFileConfigSpec;
import com.phonepe.drove.models.config.impl.InlineConfigSpec;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import javax.validation.constraints.NotEmpty;

/**
 * Specification for configuration injection into containers
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "INLINE", value = InlineConfigSpec.class),
        @JsonSubTypes.Type(name = "EXECUTOR_LOCAL_FILE", value = ExecutorLocalFileConfigSpec.class),
        @JsonSubTypes.Type(name = "CONTROLLER_HTTP_FETCH", value = ControllerHttpFetchConfigSpec.class),
        @JsonSubTypes.Type(name = "EXECUTOR_HTTP_FETCH", value = ExecutorHttpFetchConfigSpec.class),
})
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
@Data
@Schema(
    description = "Configuration specification for injecting config files into containers. " +
                  "Supports inline data, local files, or HTTP-fetched content.",
    discriminatorProperty = "type",
    discriminatorMapping = {
        @DiscriminatorMapping(value = "INLINE", schema = InlineConfigSpec.class),
        @DiscriminatorMapping(value = "EXECUTOR_LOCAL_FILE", schema = ExecutorLocalFileConfigSpec.class),
        @DiscriminatorMapping(value = "CONTROLLER_HTTP_FETCH", schema = ControllerHttpFetchConfigSpec.class),
        @DiscriminatorMapping(value = "EXECUTOR_HTTP_FETCH", schema = ExecutorHttpFetchConfigSpec.class)
    },
    subTypes = {
        InlineConfigSpec.class,
        ExecutorLocalFileConfigSpec.class,
        ControllerHttpFetchConfigSpec.class,
        ExecutorHttpFetchConfigSpec.class
    }
)
public abstract class ConfigSpec {

    @Schema(description = "Type of configuration source", requiredMode = Schema.RequiredMode.REQUIRED)
    private final ConfigSpecType type;

    @NotEmpty
    @Schema(description = "Filename to use when writing config inside the container", example = "config.yaml", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String localFilename;

    public abstract <T> T accept(final ConfigSpecVisitor<T> visitor);
}
