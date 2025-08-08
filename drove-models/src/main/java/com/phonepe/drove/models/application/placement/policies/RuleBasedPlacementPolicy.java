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
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 *
 */

@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class RuleBasedPlacementPolicy extends PlacementPolicy {

    public static final String HOPE_RULE = "HOPE";
    public static final String MVEL_RULE = "MVEL";

    @Getter
    public enum RuleType {
        HOPE(HOPE_RULE),
        MVEL(MVEL_RULE);

        private final String name;

        RuleType(String name) {
            this.name = name;
        }
    }

    @NotEmpty(message = "- Specify a rule to select/reject node based")
    String rule;

    @NotNull
    RuleType ruleType;

    public RuleBasedPlacementPolicy(String rule, RuleType ruleType) {
        super(PlacementPolicyType.RULE_BASED);
        this.rule = rule;
        this.ruleType = ruleType;
    }

    @Override
    public <T> T accept(PlacementPolicyVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
