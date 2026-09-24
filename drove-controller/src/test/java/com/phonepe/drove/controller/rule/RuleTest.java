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
package com.phonepe.drove.controller.rule;

import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.cel.CelEngine;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.CompositePlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.RuleBasedPlacementPolicy;
import com.phonepe.drove.models.info.resources.NodeSelectionContext;
import com.phonepe.drove.models.operation.rule.RuleCallStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import lombok.val;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
class RuleTest extends ControllerTestBase {
    private static final NodeSelectionContext schedulingInfoBasic = NodeSelectionContext.builder()
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
                                .node(ControllerTestUtils.allocatedExecutorNode(8080))
                                .build())
            .build();

    private static final PlacementRuleEvaluator ruleEvaluator = new PlacementRuleEvaluator(
            new CelEngine(MAPPER, 100));

    @ParameterizedTest(name = "name = {0} | policy: {1} | status : {2} | result: {3} ")
    @MethodSource("testBasicCase")
    void testCode(String name, RuleBasedPlacementPolicy policy, NodeSelectionContext obj, RuleCallStatus status, Boolean result) {

        var res = ruleEvaluator.evaluate(
                policy,
                obj
        );

        Assertions.assertEquals(status, res.getStatus(), "TESTCASE:" + name);
        Assertions.assertEquals(result, res.isResult(), "TESTCASE:" + name);
    }


    private static Stream<Arguments> testBasicCase() {
        return Stream.of(
                Arguments.of("CEL happy case",
                        RuleBasedPlacementPolicy.builder()
                                .rule("true")
                                .build(),
                        schedulingInfoBasic,
                        RuleCallStatus.SUCCESS,
                        true
                ),

                Arguments.of("CEL wrong rule format",
                        RuleBasedPlacementPolicy.builder()
                                .rule("(true &&")
                                .build(),
                        schedulingInfoBasic,
                        RuleCallStatus.FAILURE,
                        false
                ),

                Arguments.of("CEL field access happy case",
                        RuleBasedPlacementPolicy.builder()
                                .rule("ctx.allocation.node.executorId == 'Ex1'")
                                .build(),
                        schedulingInfoBasic,
                        RuleCallStatus.SUCCESS,
                        true
                ),

                Arguments.of("CEL field access negative case",
                        RuleBasedPlacementPolicy.builder()
                                .rule("ctx.allocation.node.executorId == 'wrong'")
                                .build(),
                        schedulingInfoBasic,
                        RuleCallStatus.SUCCESS,
                        false
                ),

                Arguments.of("CEL with null obj",
                        RuleBasedPlacementPolicy.builder()
                                .rule("true")
                                .build(),
                        null,
                        RuleCallStatus.FAILURE,
                        false
                )
        );
    }

    @ParameterizedTest(name = "validatePolicyRules: {0}")
    @MethodSource("validatePolicyRulesCases")
    void validatePolicyRulesReturnsExpectedResult(String name, PlacementPolicy policy, int expectedErrorCount, String expectedErrorPrefix) {
        val errors = ControllerUtils.validatePlacementPolicyRules(policy, ruleEvaluator);
        assertEquals(expectedErrorCount, errors.size(), () -> name + ": " + errors);
        if (expectedErrorCount > 0) {
            assertTrue(errors.get(0).startsWith(expectedErrorPrefix), () -> name + ": " + errors);
        }
    }

    private static Stream<Arguments> validatePolicyRulesCases() {
        return Stream.of(
                Arguments.of("valid rule",
                        RuleBasedPlacementPolicy.builder()
                                .rule("ctx.allocation.node.executorId == 'Ex1'")
                                .build(),
                        0, null),
                Arguments.of("broken rule",
                        RuleBasedPlacementPolicy.builder()
                                .rule("(true &&")
                                .build(),
                        1, "Placement rule failed to compile: (true && - Rule compilation error: ERROR: <input>:1:9: mismatched input"),
                Arguments.of("composite policy with broken rule",
                        CompositePlacementPolicy.builder()
                                .combiner(CompositePlacementPolicy.CombinerType.AND)
                                .policies(List.of(
                                        RuleBasedPlacementPolicy.builder().rule("true").build(),
                                        RuleBasedPlacementPolicy.builder().rule("abc def ghi").build()))
                                .build(),
                        1, "Placement rule failed to compile: abc def ghi - Rule compilation error: ERROR: <input>:1:5: mismatched input"),
                Arguments.of("null policy",
                        null,
                        0, null)
        );
    }

}
