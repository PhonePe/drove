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

import com.phonepe.drove.controller.cel.CelEngine;
import com.phonepe.drove.models.application.placement.policies.RuleBasedPlacementPolicy;
import com.phonepe.drove.models.info.resources.NodeSelectionContext;
import com.phonepe.drove.models.operation.rule.RuleCheckResponse;
import com.phonepe.drove.models.operation.rule.RuleEvalResponse;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Evaluates rule-based placement policies using the shared {@link CelEngine}.
 *
 * <p>Thin adapter: it only maps placement-policy types onto the general CEL
 * engine. Compilation, caching, and error handling live in {@link CelEngine}.
 */
@Slf4j
@Singleton
@AllArgsConstructor(onConstructor = @__({@Inject}))
public class PlacementRuleEvaluator {

    private final CelEngine celEngine;

    public RuleEvalResponse evaluate(final RuleBasedPlacementPolicy policy, final NodeSelectionContext context) {
        return celEngine.evaluate(policy.getRule(), context);
    }

    public RuleCheckResponse check(final RuleBasedPlacementPolicy policy) {
        return celEngine.check(policy.getRule());
    }
}
