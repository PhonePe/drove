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

package com.phonepe.drove.models.application.requirements;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Base class for resource requirements
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "CPU", value = CPURequirement.class),
        @JsonSubTypes.Type(name = "MEMORY", value = MemoryRequirement.class),
})
@Data
@Schema(
        description = "Base specification for resource requirements needed by application instances",
        discriminatorProperty = "type",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "CPU", schema = CPURequirement.class),
                @DiscriminatorMapping(value = "MEMORY", schema = MemoryRequirement.class)
        },
        subTypes = {CPURequirement.class, MemoryRequirement.class}
)
public abstract class ResourceRequirement {
    @Schema(description = "Type of resource requirement", requiredMode = Schema.RequiredMode.REQUIRED)
    private final ResourceType type;

    public abstract <T> T accept(final ResourceRequirementVisitor<T> visitor);
}
