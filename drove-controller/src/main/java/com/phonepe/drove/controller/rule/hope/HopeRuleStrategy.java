/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  You may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 */
package com.phonepe.drove.controller.rule.hope;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.rule.CachingRuleEvalStrategy;
import io.appform.hope.core.Evaluatable;
import io.appform.hope.lang.HopeLangEngine;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Objects;

@Slf4j
@Singleton
public class HopeRuleStrategy extends CachingRuleEvalStrategy<Evaluatable> {

    private final HopeLangEngine engine;
    private final ObjectMapper objectMapper;

    @Inject
    public HopeRuleStrategy(
            final ObjectMapper objectMapper,
            @Named("HopeRuleCompiledCacheSize") final Integer cacheSize) {
        super(Objects.requireNonNullElse(cacheSize, ControllerOptions.DEFAULT_COMPILED_RULE_CACHE_SIZE));
        this.objectMapper = objectMapper;
        engine = HopeLangEngine.builder()
                .addPackage("io.appform.hope")
                .autoFunctionDiscoveryEnabled(false)
                .build();
    }

    protected final Evaluatable compileRule(final String rule) {
        return engine.parse(rule);
    }

    @Override
    protected boolean evaluateRule(Evaluatable evaluatable, Object data) throws Exception {
        val jsonNode = objectMapper.valueToTree(data);
        return engine.evaluate(evaluatable, jsonNode);
    }
}