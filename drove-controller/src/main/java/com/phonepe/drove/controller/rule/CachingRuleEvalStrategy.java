/*
 *  Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.controller.rule;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.phonepe.drove.common.model.utils.Pair;
import com.phonepe.drove.models.operation.rule.RuleCallStatus;
import com.phonepe.drove.models.operation.rule.RuleCheckResponse;
import com.phonepe.drove.models.operation.rule.RuleEvalResponse;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Base class for simple loading cache based rule evaluator
 */
@Slf4j
public abstract class CachingRuleEvalStrategy<C> implements RuleEvalStrategy {
    private final LoadingCache<String, C> cachedExpr;

    protected CachingRuleEvalStrategy(int cacheSize) {
        cachedExpr = Caffeine.newBuilder()
                .maximumSize(cacheSize)
                .build(rule -> {
                    val compiled = compile(rule);
                    if (compiled.getFirst().getStatus() == RuleCallStatus.SUCCESS) {
                        log.debug("Loading evaluatable for rule: {}", rule);
                        return compiled.getSecond();
                    }
                    log.error("Could not load rule {} as there were compilation failures: {}",
                              rule, compiled.getFirst().getError());
                    return null;
                });
    }

    @Override
    public RuleCheckResponse check(String rule) {
        return compile(rule).getFirst();
    }

    @Override
    public RuleEvalResponse evaluate(String rule, Object data) {
        val compiled = compile(rule);
        try {
            val compiledRule = cachedExpr.get(rule);
            if (null != compiledRule) {
                return RuleEvalResponse.builder()
                        .status(RuleCallStatus.SUCCESS)
                        .result(evaluateRule(compiledRule, data))
                        .build();
            }
        }
        catch (Exception e) {
            log.error("Failed to evaluate rule: {}, data: {}, exception: {}", rule, data, e.getMessage());
            return RuleEvalResponse.builder()
                    .status(RuleCallStatus.FAILURE)
                    .result(false)
                    .error(e.getMessage())
                    .build();
        }

        return RuleEvalResponse.builder()
                .status(RuleCallStatus.FAILURE)
                .result(false)
                .error(compiled.getFirst().getError())
                .build();
    }

    protected final Pair<RuleCheckResponse, C> compile(final String rule) {
        try {
            val evaluatable = compileRule(rule);
            return new Pair<>(
                    RuleCheckResponse.builder()
                            .status(RuleCallStatus.SUCCESS)
                            .build(),
                    evaluatable
            );
        }
        catch (Exception e) {
            log.warn("Failed to compile rule: {}, exception: {}", rule, e.getMessage());
            return new Pair<>(
                    RuleCheckResponse.builder()
                            .status(RuleCallStatus.FAILURE)
                            .error("HopeLang Compilation error: " + e.getMessage())
                            .build(),
                    null
            );
        }
    }

    @SuppressWarnings("java:S112")
    protected abstract C compileRule(String rule) throws Exception;
    @SuppressWarnings("java:S112")
    protected abstract boolean evaluateRule(C evaluatable, Object data) throws Exception;
}
