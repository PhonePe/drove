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

package com.phonepe.drove.controller.rule.mvel;

import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.rule.CachingRuleEvalStrategy;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.Serializable;
import java.util.Objects;


@Singleton
public class MvelRuleStrategy extends CachingRuleEvalStrategy<Serializable> {

    private final SafeMvel safeMvel;

    @Inject
    public MvelRuleStrategy(@Named("MvelRuleCompiledCacheSize") final Integer cacheSize, SafeMvel safeMvel) {
        super(Objects.requireNonNullElse(cacheSize, ControllerOptions.DEFAULT_COMPILED_RULE_CACHE_SIZE));
        this.safeMvel = safeMvel;
    }

    @Override
    protected Serializable compileRule(String rule) {
        return safeMvel.compile(rule);
    }

    @Override
    protected boolean evaluateRule(Serializable evaluatable, Object data) {
        val result = safeMvel.execute(evaluatable, data);
        return result instanceof Boolean boolResult && boolResult;
    }
}
