/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  You may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.phonepe.drove.models.api;

import com.phonepe.drove.models.application.placement.policies.RuleBasedPlacementPolicy;
import com.phonepe.drove.models.info.nodedata.SchedulingInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotNull;

/**
 * Request payload for dry-run validation of placement rules
 */
@Value
@Jacksonized
@Builder
@Schema(description = "Input data for dry-run validation of rule-based placement policies against scheduling information")
public class DryRunRuleInfo {

    @Schema(description = "Rule-based placement policy to validate, containing rules for executor selection and constraints")
    @NotNull
    RuleBasedPlacementPolicy ruleBasedPlacementPolicy;

    @Schema(description = "Scheduling information containing resource requirements and constraints for the workload")
    @NotNull
    SchedulingInfo schedulingInfo;
}
