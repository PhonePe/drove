/*
*  * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
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
import com.phonepe.drove.controller.rule.hope.HopeRuleInstance;
import com.phonepe.drove.controller.rule.mvel.MvelRuleInstance;
import com.phonepe.drove.models.application.placement.policies.RuleBasedPlacementPolicy;
import com.phonepe.drove.models.info.nodedata.SchedulingInfo;
import com.phonepe.drove.models.operation.rule.RuleCallStatus;
import com.phonepe.drove.models.operation.rule.RuleEvalResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

class RuleTest extends ControllerTestBase {
    static SchedulingInfo schedulingInfoBasic = SchedulingInfo.builder()
            .applicationEnvironment(Map.of())
            .allExecutorMetadata(Map.of())
            .executorNodeId("id")
            .allocatedExecutorNodeMetadata(Map.of())
            .build();

    static RuleEvaluator ruleEvaluatorWithOnlyMvel = new RuleEvaluator(Map.of(
            RuleBasedPlacementPolicy.RuleType.MVEL, MvelRuleInstance.create()
    ));

    static RuleEvaluator ruleEvaluatorWithBoth = new RuleEvaluator(Map.of(
            RuleBasedPlacementPolicy.RuleType.MVEL, MvelRuleInstance.create(),
            RuleBasedPlacementPolicy.RuleType.HOPE, HopeRuleInstance.create(MAPPER)
    ));


    @ParameterizedTest(name = "name = {0} | evaluator: {1} | policy: {2} | status : {3} | result: {4} ")
    @MethodSource("testBasicCase")
    void testCode(String name, RuleEvaluator ruleEvaluator, RuleBasedPlacementPolicy policy, SchedulingInfo obj, RuleCallStatus status, Boolean result) {

        var res = ruleEvaluator.evaluate(
                policy,
                obj
        );

        Assertions.assertEquals(status, res.getStatus(), "TESTCASE:" + name);
        Assertions.assertEquals(result, res.isResult(), "TESTCASE:" + name);
    }


    private static Stream<Arguments> testBasicCase() {
        return Stream.of(
                Arguments.of( "happy case with mvel only", ruleEvaluatorWithOnlyMvel, RuleBasedPlacementPolicy.builder()
                        .ruleType(RuleBasedPlacementPolicy.RuleType.MVEL)
                        .rule("true")
                        .build(),
                        schedulingInfoBasic,
                        RuleCallStatus.SUCCESS,
                        true
                ),

                Arguments.of( "Wrong rule format with mvel only",ruleEvaluatorWithOnlyMvel,  RuleBasedPlacementPolicy.builder()
                        .ruleType(RuleBasedPlacementPolicy.RuleType.MVEL)
                        .rule("aksjgdkadj")
                        .build(),
                        schedulingInfoBasic,
                        RuleCallStatus.FAILURE,
                        false
                ),

                Arguments.of("Nonexisting rule type with mvel only",  ruleEvaluatorWithOnlyMvel, RuleBasedPlacementPolicy.builder()
                                .ruleType(RuleBasedPlacementPolicy.RuleType.HOPE)
                                .rule("SOME_RULE")
                                .build(),
                        schedulingInfoBasic,
                        RuleCallStatus.FAILURE,
                        false
                ),

                Arguments.of("HOPE wrong rule format with both",  ruleEvaluatorWithBoth, RuleBasedPlacementPolicy.builder()
                                .ruleType(RuleBasedPlacementPolicy.RuleType.HOPE)
                                .rule("2 asdj 4ada")
                                .build(),
                        schedulingInfoBasic,
                        RuleCallStatus.FAILURE,
                        false
                ),

                Arguments.of( "HOPE happy rule case with both", ruleEvaluatorWithBoth,  RuleBasedPlacementPolicy.builder()
                                .ruleType(RuleBasedPlacementPolicy.RuleType.HOPE)
                                .rule("2 < 4")
                                .build(),
                        schedulingInfoBasic,
                        RuleCallStatus.SUCCESS,
                        true
                ),

                Arguments.of( "correct rule case with both but null obj", ruleEvaluatorWithBoth,  RuleBasedPlacementPolicy.builder()
                                .ruleType(RuleBasedPlacementPolicy.RuleType.HOPE)
                                .rule("2 < 4")
                                .build(),
                        null,
                        RuleCallStatus.SUCCESS,
                        true
                )
        );
    }

}
