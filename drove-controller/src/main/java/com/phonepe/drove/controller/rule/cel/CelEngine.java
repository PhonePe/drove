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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.cel.common.types.MapType;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.extensions.CelExtensions;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import lombok.extern.slf4j.Slf4j;
import lombok.Builder;
import lombok.NonNull;
import lombok.val;

import java.util.Map;
import java.util.Objects;

/**
 * Thin abstraction over CEL compiler and runtime configuration.
 *
 * <p>Each {@link CelEngine} instance owns a pre-configured {@link CelCompiler}
 * and {@link CelRuntime} pair. Extensions are enabled up-front so that all
 * rule expressions can use them without waiting for a future release cycle.
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
public final class CelEngine {

    public static final String DEFAULT_CONTEXT_VAR_NAME = "ctx";

    private final CelCompiler compiler;
    private final CelRuntime runtime;
    private final ObjectMapper objectMapper;
    private final String contextVarName;

    @Builder
    private CelEngine(@NonNull final ObjectMapper objectMapper, final String contextVarName) {
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
    }

    /**
     * Compiles and plans a CEL expression into an executable, immutable
     * {@link CelRuntime.Program}.
     *
     * <p>This performs the full parse, type-check and program-planning pipeline
     * once. The returned {@link CelRuntime.Program} is stateless and thread-safe,
     * so it can be cached and evaluated concurrently against different data.
     *
     * @param expression the CEL expression to compile and plan.
     * @return the executable program.
     * @throws Exception if compilation (syntax/type error) or planning fails.
     */
    @SuppressWarnings("java:S112")
    public CelRuntime.Program plan(final String expression) throws Exception {
        val ast = compiler.compile(expression).getAst();
        return runtime.createProgram(ast);
    }

    /**
     * Evaluates a pre-planned CEL {@link CelRuntime.Program} against the provided
     * data object.
     *
     * <p>The data object is converted to a {@code Map<String, Object>} via Jackson
     * before being bound to the context variable. The result must be a boolean.
     *
     * <p>The program is immutable and holds no per-evaluation state, so a single
     * cached program may be evaluated safely from multiple threads.
     *
     * @param program the compiled and planned program.
     * @param data    the data object to evaluate against (must not be null).
     * @return the boolean result of the evaluation.
     * @throws Exception if evaluation fails.
     */
    @SuppressWarnings("java:S112")
    public boolean evaluate(final CelRuntime.Program program, final Object data)
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
