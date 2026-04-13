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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotEmpty;

/**
 * Command-based health check specification
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
@Schema(description = "Command-based health check that executes a command inside the container to verify instance health")
public class CmdCheckModeSpec extends CheckModeSpec {

    @NotEmpty
    @Schema(description = "Command to execute inside the container. Exit code 0 indicates healthy.",
            example = "/app/health-check.sh", requiredMode = Schema.RequiredMode.REQUIRED)
    String command;

    @Schema(description = "Whether to disable shell interpretation. If true, command is executed directly without shell.",
            example = "false")
    boolean shellDisabled;

    public CmdCheckModeSpec(String command, boolean shellDisabled) {
        super(CheckMode.CMD);
        this.command = command;
        this.shellDisabled = shellDisabled;
    }

    @Override
    public <T> T accept(CheckModeSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
