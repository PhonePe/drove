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
package com.phonepe.drove.controller.cel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.phonepe.drove.common.model.utils.Pair;
import com.phonepe.drove.models.operation.rule.RuleCallStatus;
import com.phonepe.drove.models.operation.rule.RuleCheckResponse;
import com.phonepe.drove.models.operation.rule.RuleEvalResponse;
import dev.cel.common.types.MapType;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.extensions.CelExtensions;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import lombok.extern.slf4j.Slf4j;
import lombok.NonNull;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.Map;
import java.util.Objects;

/**
 * General-purpose CEL (Common Expression Language) evaluator.
 *
 * <p>Owns a pre-configured {@link CelCompiler} and {@link CelRuntime} pair,
 * plus a cache of compiled programs. Any part of the controller can reuse
 * this engine to evaluate boolean CEL expressions against arbitrary data.
 *
 * <p>The data object is converted to a {@code Map<String, Object>} via Jackson
 * and bound to the CEL variable {@code "ctx"}. Field selection
 * ({@code ctx.allocation.node.executorId}) works natively on maps.
 *
 * <p>Enabled extensions:
 * <ul>
 *   <li>{@link CelExtensions#strings()} — {@code startsWith}, {@code matches}, etc.</li>
 *   <li>{@link CelExtensions#bindings()} — {@code cel.bind()} local variables (compiler-only)</li>
 *   <li>{@link CelExtensions#comprehensions()} — v2 comprehension macros: {@code filter()}, {@code map()}, {@code exists()}, {@code all()}</li>
 *   <li>{@link CelExtensions#lists()} — list operations</li>
 *   <li>{@link CelExtensions#math()} — {@code math.greatest()}, {@code math.least()}</li>
 *   <li>{@link CelStandardMacro#STANDARD_MACROS} — standard macros including {@code has()}</li>
 * </ul>
 *
 * <p>CEL is non-Turing-complete (no loops, no recursion, no side effects),
 * making it inherently safe without custom sandboxing.
 */
@Slf4j
@Singleton
public class CelEngine {

    public static final String DEFAULT_CONTEXT_VAR_NAME = "ctx";
    public static final int DEFAULT_COMPILED_PROGRAM_CACHE_SIZE = 100;

    private final CelCompiler compiler;
    private final CelRuntime runtime;
    private final ObjectMapper objectMapper;
    private final String contextVarName;
    private final LoadingCache<String, CelRuntime.Program> cachedPrograms;

    @Inject
    public CelEngine(final ObjectMapper objectMapper, final Integer compiledProgramCacheSize) {
        this(objectMapper, DEFAULT_CONTEXT_VAR_NAME, compiledProgramCacheSize);
    }

    public CelEngine(
            @NonNull final ObjectMapper objectMapper,
            final String contextVarName,
            final Integer compiledProgramCacheSize) {
        this.objectMapper = objectMapper;
        this.contextVarName = Objects.requireNonNullElse(contextVarName,
                DEFAULT_CONTEXT_VAR_NAME);

        this.compiler = CelCompilerFactory.standardCelCompilerBuilder()
                .addLibraries(
                        CelExtensions.strings(),
                        CelExtensions.bindings(),
                        CelExtensions.comprehensions(),
                        CelExtensions.lists(),
                        CelExtensions.math())
                .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
                .addVar(this.contextVarName, MapType.create(SimpleType.STRING, SimpleType.DYN))
                .setResultType(SimpleType.BOOL)
                .build();

        this.runtime = CelRuntimeFactory.standardCelRuntimeBuilder()
                .addLibraries(
                        CelExtensions.strings(),
                        CelExtensions.comprehensions(),
                        CelExtensions.lists(),
                        CelExtensions.math())
                .build();

        this.cachedPrograms = Caffeine.newBuilder()
                .maximumSize(Objects.requireNonNullElse(compiledProgramCacheSize,
                                                         DEFAULT_COMPILED_PROGRAM_CACHE_SIZE))
                .build(rule -> {
                    val compiled = compile(rule);
                    if (compiled.getFirst().getStatus() == RuleCallStatus.SUCCESS) {
                        log.debug("Loaded compiled program for rule: {}", rule);
                        return compiled.getSecond();
                    }
                    log.error("Could not compile rule {} as there were compilation failures: {}",
                              rule, compiled.getFirst().getError());
                    return null;
                });
    }

    /**
     * Compiles a CEL expression into an executable, immutable
     * {@link CelRuntime.Program}.
     *
     * <p>This performs the full parse, type-check and program-planning pipeline
     * once. The returned {@link CelRuntime.Program} is stateless and thread-safe,
     * so it can be cached and evaluated concurrently against different data.
     *
     * @param expression the CEL expression to compile.
     * @return a pair of {@link RuleCheckResponse} and the compiled program.
     *         The response status is SUCCESS when compilation succeeds;
     *         otherwise it is FAILURE and the program is null.
     */
    @SuppressWarnings("java:S112")
    public Pair<RuleCheckResponse, CelRuntime.Program> compile(final String expression) {
        try {
            val ast = compiler.compile(expression).getAst();
            return new Pair<>(
                    RuleCheckResponse.builder()
                            .status(RuleCallStatus.SUCCESS)
                            .build(),
                    runtime.createProgram(ast));
        }
        catch (Exception e) {
            log.warn("Failed to compile rule: {}, exception: {}", expression, e.getMessage());
            return new Pair<>(
                    RuleCheckResponse.builder()
                            .status(RuleCallStatus.FAILURE)
                            .error("Rule compilation error: " + e.getMessage())
                            .build(),
                    null);
        }
    }

    /**
     * Compiles a rule (from the program cache) and validates it.
     *
     * @param expression the CEL expression to check.
     * @return a {@link RuleCheckResponse} with the compilation status.
     */
    public RuleCheckResponse check(final String expression) {
        return compile(expression).getFirst();
    }

    /**
     * Compiles a rule (from the program cache, when not already compiled) and
     * evaluates it as a boolean expression against the provided data object.
     *
     * <p>Programs are immutable and hold no per-evaluation state, so a single
     * cached program may be evaluated safely from multiple threads.
     *
     * @param expression the CEL expression to evaluate.
     * @param data       the data object to evaluate against.
     * @return a {@link RuleEvalResponse} with the evaluation status and result.
     */
    @SuppressWarnings("java:S112")
    public RuleEvalResponse evaluate(final String expression, final Object data) {
        try {
            val program = cachedPrograms.get(expression);
            if (null != program) {
                return RuleEvalResponse.builder()
                        .status(RuleCallStatus.SUCCESS)
                        .result(evaluateProgram(program, data))
                        .build();
            }
            // Cache loader returned null → compilation failed (already logged by the loader)
            return RuleEvalResponse.builder()
                    .status(RuleCallStatus.FAILURE)
                    .result(false)
                    .error("Rule compilation failed for: " + expression)
                    .build();
        }
        catch (Exception e) {
            log.error("Failed to evaluate rule: {}, exception: {}", expression, e.getMessage());
            return RuleEvalResponse.builder()
                    .status(RuleCallStatus.FAILURE)
                    .result(false)
                    .error(e.getMessage())
                    .build();
        }
    }

    /**
     * Evaluates a pre-compiled {@link CelRuntime.Program} against the provided
     * data object.
     *
     * <p>The data object is converted to a {@code Map<String, Object>} via Jackson
     * before being bound to the context variable. The result must be a boolean.
     *
     * @param program the compiled program.
     * @param data    the data object to evaluate against (must not be null).
     * @return the boolean result of the evaluation.
     * @throws Exception if evaluation fails.
     */
    @SuppressWarnings("java:S112")
    public boolean evaluateProgram(final CelRuntime.Program program, final Object data)
            throws Exception {
        if (data == null) {
            throw new IllegalArgumentException("Rule data cannot be null");
        }
        val ctxMap = objectMapper.convertValue(data,
                new TypeReference<Map<String, Object>>() {});
        val result = program.eval(Map.of(contextVarName, ctxMap));
        return result instanceof Boolean b && b;
    }
}
