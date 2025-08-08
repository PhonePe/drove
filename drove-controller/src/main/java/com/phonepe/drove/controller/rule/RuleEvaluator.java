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

package com.phonepe.drove.controller.rule;

import com.phonepe.drove.models.application.placement.policies.RuleBasedPlacementPolicy;
import com.phonepe.drove.models.info.nodedata.SchedulingInfo;
import com.phonepe.drove.models.operation.rule.RuleCallStatus;
import com.phonepe.drove.models.operation.rule.RuleCheckResponse;
import com.phonepe.drove.models.operation.rule.RuleEvalResponse;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

@Slf4j
@Singleton
public class RuleEvaluator {
    private final Map<RuleBasedPlacementPolicy.RuleType, RuleEvalStrategy> ruleEvalStrategies;

    @Inject
    public RuleEvaluator(Map<RuleBasedPlacementPolicy.RuleType, RuleEvalStrategy> ruleEvalStrategies) {
        this.ruleEvalStrategies = ruleEvalStrategies;
    }

    public RuleEvalResponse evaluate(RuleBasedPlacementPolicy ruleBasedPlacementPolicy, SchedulingInfo schedulingInfo) {
        if ( ! ruleEvalStrategies.containsKey(ruleBasedPlacementPolicy.getRuleType()) ) {
            log.warn("Rule type {} not supported",
                    ruleBasedPlacementPolicy.getRuleType()
            );
            return RuleEvalResponse.builder()
                    .status(RuleCallStatus.FAILURE)
                    .error("Unregistered RuleType :" + ruleBasedPlacementPolicy.getRuleType())
                    .build();
        }

        return ruleEvalStrategies.get(ruleBasedPlacementPolicy.getRuleType())
                .evaluate(
                        ruleBasedPlacementPolicy.getRule(),
                        schedulingInfo
                );

    }

    public RuleCheckResponse check(RuleBasedPlacementPolicy ruleBasedPlacementPolicy) {
        if ( ! ruleEvalStrategies.containsKey(ruleBasedPlacementPolicy.getRuleType()) ) {
            log.warn("Rule type {} not supported",
                    ruleBasedPlacementPolicy.getRuleType()
            );
            return RuleCheckResponse.builder()
                    .status(RuleCallStatus.FAILURE)
                    .error("Unregistered RuleType :" + ruleBasedPlacementPolicy.getRuleType())
                    .build();
        }

        return ruleEvalStrategies.get(ruleBasedPlacementPolicy.getRuleType())
                .check(ruleBasedPlacementPolicy.getRule());
    }

}
