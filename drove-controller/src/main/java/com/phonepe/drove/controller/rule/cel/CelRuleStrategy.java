/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.phonepe.drove.controller.rule.cel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.rule.CachingRuleEvalStrategy;
import com.phonepe.drove.models.info.resources.NodeSelectionContext;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.types.MapType;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.extensions.CelExtensions;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Objects;

/**
 * CEL (Common Expression Language) based rule evaluation strategy.
 *
 * <p>The {@link NodeSelectionContext} is converted to a {@code Map<String, Object>}
 * via Jackson before being passed to CEL. This avoids issues with interface-typed
 * fields (e.g. {@code DeploymentSpec}) and enum-typed fields (e.g. {@code ExecutorState})
 * that CEL's native type extensions cannot handle.
 *
 * <p>CEL field selection ({@code ctx.allocation.node.executorId}) works natively
 * on maps — {@code ctx.allocation} is equivalent to {@code ctx["allocation"]}.
 * All nested objects become {@code LinkedHashMap} instances, enums become their
 * string names, and collections become {@code ArrayList}/{@code LinkedHashMap}.
 *
 * <p>CEL is non-Turing-complete (no loops, no recursion, no side effects),
 * making it inherently safe without the custom sandboxing that MVEL requires.
 */
@Slf4j
@Singleton
public class CelRuleStrategy extends CachingRuleEvalStrategy<CelAbstractSyntaxTree> {

    private static final CelCompiler CEL_COMPILER =
            CelCompilerFactory.standardCelCompilerBuilder()
                    .addLibraries(CelExtensions.strings())
                    .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
                    .addVar("ctx", MapType.create(SimpleType.STRING, SimpleType.DYN))
                    .setResultType(SimpleType.BOOL)
                    .build();

    private static final CelRuntime CEL_RUNTIME =
            CelRuntimeFactory.standardCelRuntimeBuilder()
                    .addLibraries(CelExtensions.strings())
                    .build();

    private final ObjectMapper objectMapper;

    @Inject
    public CelRuleStrategy(
            final ObjectMapper objectMapper,
            @Named("CelRuleCompiledCacheSize") final Integer cacheSize) {
        super(Objects.requireNonNullElse(cacheSize,
                ControllerOptions.DEFAULT_COMPILED_RULE_CACHE_SIZE));
        this.objectMapper = objectMapper;
    }

    CelRuleStrategy(final ObjectMapper objectMapper, final int cacheSize) {
        super(cacheSize);
        this.objectMapper = objectMapper;
    }

    @Override
    protected CelAbstractSyntaxTree compileRule(final String rule) throws Exception {
        return CEL_COMPILER.compile(rule).getAst();
    }

    @Override
    protected boolean evaluateRule(final CelAbstractSyntaxTree ast, final Object data)
            throws Exception {
        if (data == null) {
            throw new IllegalArgumentException("Rule data cannot be null");
        }
        val ctxMap = objectMapper.convertValue(data,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        CelRuntime.Program program = CEL_RUNTIME.createProgram(ast);
        Object result = program.eval(Map.of("ctx", ctxMap));
        return result instanceof Boolean b && b;
    }
}
