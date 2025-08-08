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

package com.phonepe.drove.controller.rule.mvel;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.rule.RuleEvalStrategy;
import com.phonepe.drove.models.operation.rule.RuleCallStatus;
import com.phonepe.drove.models.operation.rule.RuleCheckResponse;
import com.phonepe.drove.models.operation.rule.RuleEvalResponse;
import io.appform.functionmetrics.Pair;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.mvel2.MVEL;
import org.mvel2.compiler.ExecutableStatement;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.Serializable;
import java.util.Objects;


@Slf4j
@Singleton
public class MvelRuleStrategy implements RuleEvalStrategy {
    private final Cache<String, ExecutableStatement> compiledExpressionMap;

    @Inject
    public MvelRuleStrategy(final @Named("MvelRuleCompiledCacheSize") Integer cacheSize) {
        compiledExpressionMap = Caffeine.<String, ExecutableStatement> newBuilder()
                .maximumSize(Objects.requireNonNullElse(cacheSize, ControllerOptions.DEFAULT_COMPILED_RULE_CACHE_SIZE))
                .build();
    }

    @Override
    public RuleCheckResponse check(final String rule) {
        return compile(rule)
                .getKey();
    }

    private Pair<RuleCheckResponse, ExecutableStatement> compile(final String rule) {
        try {
            val serializable = MVEL.compileExpression(rule);

            if (serializable instanceof ExecutableStatement executableStatement) {
                return new Pair<>(
                        RuleCheckResponse.builder()
                                .status(RuleCallStatus.SUCCESS)
                                .build(),
                        executableStatement
                );
            }
            log.warn("Failed to compile rule: {}", rule);

            return new Pair<>(
                    RuleCheckResponse.builder()
                            .status(RuleCallStatus.FAILURE)
                            .error("Failed to convert MVEL statement to ExecutableStatement")
                            .build(),
                    null
            );
        } catch (Exception e) {
            log.warn("Failed to compile rule: {}, exception: e", rule, e);
            return new Pair<>(
                    RuleCheckResponse.builder()
                            .status(RuleCallStatus.FAILURE)
                            .error("MVEL Compilation error: " + e.getMessage())
                            .build(),
                    null
            );
        }

    }

    @Override
    public RuleEvalResponse evaluate(String rule, Object data) {

        val compiled = compile(rule);
        try {
            if (compiled.getKey().getStatus() == RuleCallStatus.SUCCESS) {
                val expr = compiled.getValue();
                compiledExpressionMap.put(rule, expr);
                var result = MVEL.executeExpression(expr, data);
                return RuleEvalResponse.builder()
                        .status(RuleCallStatus.SUCCESS)
                        .result(result instanceof Boolean boolResult && boolResult)
                        .build();
            }
        } catch (Exception e) {
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
