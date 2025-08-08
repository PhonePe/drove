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
package com.phonepe.drove.controller.rule.hope;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.rule.RuleEvalStrategy;
import com.phonepe.drove.models.operation.rule.RuleCallStatus;
import com.phonepe.drove.models.operation.rule.RuleCheckResponse;
import com.phonepe.drove.models.operation.rule.RuleEvalResponse;
import io.appform.functionmetrics.Pair;
import io.appform.hope.core.Evaluatable;
import io.appform.hope.lang.HopeLangEngine;
import jdk.jfr.Name;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Objects;

@Slf4j
@Singleton
public class HopeRuleStrategy implements RuleEvalStrategy {

    private final HopeLangEngine engine;
    private final ObjectMapper objectMapper;
    private final Cache<String, Evaluatable> cachedExpr;

    @Inject
    public HopeRuleStrategy(final ObjectMapper objectMapper,
                            @Named("HopeRuleCompiledCacheSize") final Integer cacheSize) {
        this.objectMapper = objectMapper;
        engine = HopeLangEngine.builder().build();

        cachedExpr = Caffeine.<String, Evaluatable>newBuilder()
                .maximumSize(Objects.requireNonNullElse(cacheSize, ControllerOptions.DEFAULT_COMPILED_RULE_CACHE_SIZE))
                .build();
    }

    @Override
    public RuleCheckResponse check(final String rule) {
        return compile(rule).getKey();
    }

    private Pair<RuleCheckResponse, Evaluatable> compile(final String rule) {
        try {
            val evaluatable = engine.parse(rule);
            return new Pair<>(
                    RuleCheckResponse.builder()
                            .status(RuleCallStatus.SUCCESS)
                            .build(),
                    evaluatable
            );
        } catch (Exception e) {
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

    @Override
    public RuleEvalResponse evaluate(final String rule, final Object data) {
        val compiled = compile(rule);
        try {
            if (compiled.getKey().getStatus() == RuleCallStatus.SUCCESS) {
                val evaluatable = compiled.getValue();
                cachedExpr.put(rule, evaluatable);
                val jsonNode = objectMapper.valueToTree(data);
                val result = engine.evaluate(evaluatable, jsonNode);
                return RuleEvalResponse.builder()
                        .status(RuleCallStatus.SUCCESS)
                        .result(result)
                        .build();
            }
        } catch (Exception e) {
            log.warn("Failed to evaluate rule: {}, data: {}, exception: {}", rule, data, e.getMessage());
            return RuleEvalResponse.builder()
                    .status(RuleCallStatus.FAILURE)
                    .result(false)
                    .error(e.getMessage())
                    .build();
        }

        return RuleEvalResponse.builder()
                .status(RuleCallStatus.FAILURE)
                .result(false)
                .error(compiled.getKey().getError())
                .build();
    }
}