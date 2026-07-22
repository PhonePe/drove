/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
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
package com.phonepe.drove.controller.rule.cel;

import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.rule.CachingRuleEvalStrategy;

import dev.cel.runtime.CelRuntime;
import lombok.extern.slf4j.Slf4j;

/**
 * CEL (Common Expression Language) based rule evaluation strategy.
 *
 * <p>Delegates compiler/runtime configuration to {@link CelEngine}, which enables
 * all safe CEL extensions (strings, bindings, comprehensions, lists, math) up-front
 * so that users can write expressive rules without waiting for future releases.
 *
 * <p>The rule data object is converted to a {@code Map<String, Object>} via Jackson
 * and bound to the CEL variable {@code "ctx"}. CEL field selection
 * ({@code ctx.allocation.node.executorId}) works natively on maps.
 *
 * <p>CEL is non-Turing-complete (no loops, no recursion, no side effects),
 * making it inherently safe without the custom sandboxing that MVEL requires.
 */
@Slf4j
@Singleton
public class CelRuleStrategy extends CachingRuleEvalStrategy<CelRuntime.Program> {

    private final CelEngine celEngine;

    @Inject
    public CelRuleStrategy(
            final ObjectMapper objectMapper,
            @Named("CelRuleCompiledCacheSize") final Integer cacheSize) {
        super(Objects.requireNonNullElse(cacheSize,
                ControllerOptions.DEFAULT_COMPILED_RULE_CACHE_SIZE));
        this.celEngine = CelEngine.builder()
                .objectMapper(objectMapper)
                .build();
    }

    @Override
    protected CelRuntime.Program compileRule(final String rule) throws Exception {
        return celEngine.plan(rule);
    }

    @Override
    protected boolean evaluateRule(final CelRuntime.Program program, final Object data)
            throws Exception {
        return celEngine.evaluate(program, data);
    }
}
