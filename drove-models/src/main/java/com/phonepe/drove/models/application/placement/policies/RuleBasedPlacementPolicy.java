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

package com.phonepe.drove.models.application.placement.policies;

import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.application.placement.PlacementPolicyType;
import com.phonepe.drove.models.application.placement.PlacementPolicyVisitor;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;


/**
 * Placement policy using CEL rules for placement decisions
 */

@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
@Schema(description = "Placement policy that uses CEL rules for flexible container placement decisions")
public class RuleBasedPlacementPolicy extends PlacementPolicy {

    public static final int MAX_RULE_LENGTH = 1000;

    @NotEmpty(message = "- Specify a rule to select/reject node based")
    @Size(max = MAX_RULE_LENGTH, message = "- Rule must not exceed " + MAX_RULE_LENGTH + " characters")
    @Schema(description = "The CEL rule expression used to evaluate host eligibility", example = "ctx.allocation.node.executorId != 'excluded-host'", requiredMode = Schema.RequiredMode.REQUIRED)
    String rule;

    public RuleBasedPlacementPolicy(String rule) {
        super(PlacementPolicyType.RULE_BASED);
        this.rule = rule;
    }


    @Override
    public <T> T accept(PlacementPolicyVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
