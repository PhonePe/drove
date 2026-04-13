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

package com.phonepe.drove.models.application.checks;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Base class for check mode specifications
 */
@JsonTypeInfo(use =  JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "HTTP", value = HTTPCheckModeSpec.class),
        @JsonSubTypes.Type(name = "CMD", value = CmdCheckModeSpec.class),
})
@Data
@Schema(
        description = "Base specification for check modes defining how health/readiness checks are performed",
        discriminatorProperty = "type",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "HTTP", schema = HTTPCheckModeSpec.class),
                @DiscriminatorMapping(value = "CMD", schema = CmdCheckModeSpec.class)
        },
        subTypes = {HTTPCheckModeSpec.class, CmdCheckModeSpec.class}
)
public abstract class CheckModeSpec {
    @Schema(description = "Type of check mode", requiredMode = Schema.RequiredMode.REQUIRED)
    private final CheckMode type;

    public abstract <T> T accept(final CheckModeSpecVisitor<T> visitor);
}
