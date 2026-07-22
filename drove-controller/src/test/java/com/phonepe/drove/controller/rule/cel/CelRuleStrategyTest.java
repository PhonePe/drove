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
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.models.info.resources.NodeSelectionContext;
import com.phonepe.drove.models.operation.rule.RuleCallStatus;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CelRuleStrategyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Build a test NodeSelectionContext with a known executor node.
     * Uses ControllerTestUtils.allocatedExecutorNode() which creates:
     *   executorId="Ex1", hostname="localhost", port=8080, tags={}, metadata={}
     */
    private static NodeSelectionContext testContext() {
        return NodeSelectionContext.builder()
                .session(NodeSelectionContext.SchedulingSession.builder()
                                 .sessionId("test-session")
                                 .build())
                .environment(NodeSelectionContext.SchedulingEnvironment.builder()
                                 .liveExecutors(Map.of())
                                 .variables(Map.of("region", "us-east"))
                                 .build())
                .allocation(NodeSelectionContext.NodeAllocation.builder()
                                 .spec(ControllerTestUtils.appSpec())
                                 .instanceId("test-instance")
                                 .node(ControllerTestUtils.allocatedExecutorNode(8080))
                                 .build())
                .build();
    }

    /**
     * Build a NodeSelectionContext with a custom executor node (tags, metadata).
     */
    private static NodeSelectionContext testContextWithTagsAndMetadata(
            Set<String> tags, Map<String, String> metadata) {
        val node = ControllerTestUtils.allocatedExecutorNode(8080);
        val customNode = new com.phonepe.drove.models.info.resources.allocation.AllocatedExecutorNode(
                node.getExecutorId(),
                node.getHostname(),
                node.getPort(),
                node.getTransportType(),
                node.getCpu(),
                node.getMemory(),
                node.getGpu(),
                tags,
                metadata,
                node.getExecutorState()
        );
        return NodeSelectionContext.builder()
                .session(NodeSelectionContext.SchedulingSession.builder()
                                 .sessionId("test-session")
                                 .build())
                .environment(NodeSelectionContext.SchedulingEnvironment.builder()
                                 .liveExecutors(Map.of())
                                 .variables(Map.of("region", "us-east"))
                                 .build())
                .allocation(NodeSelectionContext.NodeAllocation.builder()
                                 .spec(ControllerTestUtils.appSpec())
                                 .instanceId("test-instance")
                                 .node(customNode)
                                 .build())
                .build();
    }

    @Test
    void testBasicBooleanRules() {
        val rs = CelRuleInstance.create(MAPPER, 0);
        val ctx = testContext();

        // Constant true
        assertTrue(rs.evaluate("true", ctx).isResult());
        // Constant false
        assertFalse(rs.evaluate("false", ctx).isResult());

        // Field access: executorId == "Ex1"
        assertTrue(rs.evaluate("ctx.allocation.node.executorId == 'Ex1'", ctx).isResult());
        assertFalse(rs.evaluate("ctx.allocation.node.executorId == 'other'", ctx).isResult());

        // hostname == "localhost"
        assertTrue(rs.evaluate("ctx.allocation.node.hostname == 'localhost'", ctx).isResult());

        // session ID
        assertTrue(rs.evaluate("ctx.session.sessionId == 'test-session'", ctx).isResult());

        // instanceId
        assertTrue(rs.evaluate("ctx.allocation.instanceId == 'test-instance'", ctx).isResult());
    }

    @Test
    void testTagsAndMetadata() {
        val rs = CelRuleInstance.create(MAPPER, 0);
        val ctx = testContextWithTagsAndMetadata(
                Set.of("gpu", "high-memory"),
                Map.of("rack", "r1", "dc", "dc1")
        );

        // "gpu" in tags
        assertTrue(rs.evaluate("\"gpu\" in ctx.allocation.node.tags", ctx).isResult());
        assertFalse(rs.evaluate("\"cpu\" in ctx.allocation.node.tags", ctx).isResult());

        // metadata map access
        assertTrue(rs.evaluate("ctx.allocation.node.metadata[\"rack\"] == 'r1'", ctx).isResult());
        assertTrue(rs.evaluate("ctx.allocation.node.metadata[\"dc\"] == 'dc1'", ctx).isResult());
        assertFalse(rs.evaluate("ctx.allocation.node.metadata[\"rack\"] == 'r2'", ctx).isResult());
    }

    @Test
    void testEnvironmentVariables() {
        val rs = CelRuleInstance.create(MAPPER, 0);
        val ctx = testContext();

        assertTrue(rs.evaluate("ctx.environment.variables[\"region\"] == 'us-east'", ctx).isResult());
        assertFalse(rs.evaluate("ctx.environment.variables[\"region\"] == 'us-west'", ctx).isResult());
    }

    @Test
    void testStringExtensions() {
        val rs = CelRuleInstance.create(MAPPER, 0);
        val ctx = testContext();

        // startsWith
        assertTrue(rs.evaluate("ctx.allocation.node.hostname.startsWith('local')", ctx).isResult());
        // contains
        assertTrue(rs.evaluate("ctx.allocation.node.hostname.contains('ocal')", ctx).isResult());
        // matches (regex via string extensions)
        assertTrue(rs.evaluate("ctx.allocation.node.hostname.matches('local.*')", ctx).isResult());
        assertFalse(rs.evaluate("ctx.allocation.node.hostname.matches('^remote.*')", ctx).isResult());
    }

    @Test
    void testHasMacro() {
        val rs = CelRuleInstance.create(MAPPER, 0);
        val ctx = testContext();

        // has() macro on metadata
        assertTrue(rs.evaluate("has(ctx.allocation.node.metadata)", ctx).isResult());
    }

    @Test
    void testSyntaxErrorReturnsFailure() {
        val rs = CelRuleInstance.create(MAPPER, 0);
        val ctx = testContext();

        // Syntax error — unbalanced parens
        assertEquals(RuleCallStatus.FAILURE, rs.evaluate("(true &&", ctx).getStatus());

        // Nonexistent field — runtime error (key not found in map)
        assertEquals(RuleCallStatus.FAILURE,
                     rs.evaluate("ctx.allocation.node.nonexistentField == 'x'", ctx).getStatus());
    }

    @Test
    void testNullDataReturnsFailure() {
        val rs = CelRuleInstance.create(MAPPER, 0);
        val result = rs.evaluate("ctx.allocation.node.executorId == 'Ex1'", null);
        assertEquals(RuleCallStatus.FAILURE, result.getStatus());
    }

    /**
     * Regression test for thread-safety (mirrors the MVEL staging stall test).
     * CEL compiler and runtime are immutable and thread-safe by design,
     * but we verify this explicitly.
     */
    @Test
    void concurrentEvaluationIsThreadSafeAndCorrect()
            throws InterruptedException, ExecutionException {
        val strategy = CelRuleInstance.create(MAPPER, 50);
        val ctx = testContext();

        val rules = List.of(
                "ctx.allocation.node.executorId == 'Ex1'",
                "ctx.allocation.node.hostname == 'localhost'",
                "ctx.session.sessionId == 'test-session'",
                "ctx.allocation.instanceId == 'test-instance'",
                "\"gpu\" in ctx.allocation.node.tags",
                "ctx.environment.variables[\"region\"] == 'us-east'"
        );

        val threadCount = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            tasks.add(() -> {
                for (val rule : rules) {
                    val result = strategy.evaluate(rule, ctx);
                    assertEquals(RuleCallStatus.SUCCESS, result.getStatus(),
                                 "Rule evaluation must succeed: " + rule);
                }
                return null;
            });
        }

        List<Future<Void>> futures = pool.invokeAll(tasks, 10, TimeUnit.SECONDS);
        pool.shutdown();

        for (val f : futures) {
            f.get();
        }
    }

    @ParameterizedTest(name = "[{0}] {1}")
    @MethodSource("ruleCheckCases")
    void ruleCheckReturnsExpectedStatus(RuleCallStatus expectedStatus, String expression) {
        val strategy = CelRuleInstance.create(MAPPER, 50);
        val result = strategy.check(expression);
        assertEquals(expectedStatus, result.getStatus(),
                     "Unexpected status for expression: " + expression);
    }

    static Stream<Arguments> ruleCheckCases() {
        return Stream.of(
                // Valid rules
                Arguments.of(RuleCallStatus.SUCCESS, "true"),
                Arguments.of(RuleCallStatus.SUCCESS, "false"),
                Arguments.of(RuleCallStatus.SUCCESS, "1 == 1"),
                Arguments.of(RuleCallStatus.SUCCESS, "ctx.allocation.node.executorId == 'node1'"),
                Arguments.of(RuleCallStatus.SUCCESS, "\"gpu\" in ctx.allocation.node.tags"),
                Arguments.of(RuleCallStatus.SUCCESS, "ctx.allocation.node.metadata[\"rack\"] == 'r1'"),
                Arguments.of(RuleCallStatus.SUCCESS, "ctx.environment.variables[\"region\"] == 'us-east'"),
                Arguments.of(RuleCallStatus.SUCCESS, "ctx.session.sessionId == 'sess-123'"),
                Arguments.of(RuleCallStatus.SUCCESS, "ctx.allocation.node.hostname.startsWith('node')"),
                Arguments.of(RuleCallStatus.SUCCESS, "ctx.allocation.node.hostname.matches('node.*')"),
                Arguments.of(RuleCallStatus.SUCCESS, "has(ctx.allocation.node.metadata)"),
                // filter() macro requires comprehension extensions — not enabled here
                Arguments.of(RuleCallStatus.FAILURE,
                             "size(filter(ctx.environment.liveExecutors, k, v, \"gpu\" in v.tags)) > 1"),

                // Invalid rules — syntax errors
                Arguments.of(RuleCallStatus.FAILURE, "(true &&"),
                Arguments.of(RuleCallStatus.FAILURE, "true AND false"),  // CEL uses && not AND
                Arguments.of(RuleCallStatus.FAILURE, "ctx.allocation.node.executorId =="),
                Arguments.of(RuleCallStatus.FAILURE, "abc def ghi"),

                // CEL is inherently safe — these fail because identifiers are unknown
                Arguments.of(RuleCallStatus.FAILURE, "Runtime.getRuntime().exec('id')"),
                Arguments.of(RuleCallStatus.FAILURE, "java.lang.Runtime"),
                Arguments.of(RuleCallStatus.FAILURE, "System.exit(0)")
        );
    }
}
