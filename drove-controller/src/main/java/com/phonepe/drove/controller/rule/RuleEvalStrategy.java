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


import com.phonepe.drove.models.operation.rule.RuleCheckResponse;
import com.phonepe.drove.models.operation.rule.RuleEvalResponse;

/**
 * Interface for rule evaluation strategies.
 * Provides methods to check and evaluate rules.
 */
public interface RuleEvalStrategy {

    /**
     * Checks the syntax validation of a given rule.
     *
     * @param rule The rule to be checked.
     * @return A {@link RuleCheckResponse} indicating the status of the rule check.
     */
    RuleCheckResponse check(String rule);

    /**
     * Evaluates a given rule against the provided data. If the evaluation is successful, it returns boolean as response in the RuleEvalResponse
     *
     * @param rule The rule to be evaluated.
     * @param data The data to be used for evaluation.
     * @return A {@link RuleEvalResponse} containing the result of the evaluation.
     */
    RuleEvalResponse evaluate(String rule, Object data);

}
