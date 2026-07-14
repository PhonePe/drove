/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.drove.controller.securityprofile;

import com.phonepe.drove.models.application.JobType;
import com.phonepe.drove.models.policies.ContainerSecurityProfile;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ContainerSecurityPolicyConfigTest {

    @ParameterizedTest(name = "rule={0}")
    @MethodSource("validRuleTestCases")
    void testParsedPolicyRule(String rule,
                                  Set<JobType> expectedTypes,
                                  String matchingName,
                                  String nonMatchingName) {
        val parsed = ContainerSecurityPolicyConfig.ParsedPolicyRule.from(rule);

        assertEquals(expectedTypes, parsed.getApplicableTypes());
        assertTrue(matchingName.matches(parsed.getNamePattern().pattern()));
        if (nonMatchingName != null) {
            assertFalse(nonMatchingName.matches(parsed.getNamePattern().pattern()));
        }
    }

    @ParameterizedTest(name = "rule={0}")
    @MethodSource("invalidRuleTestCases")
    void testParsedPolicyRuleInvalid(String rule) {
        assertThrows(IllegalArgumentException.class,
                     () -> ContainerSecurityPolicyConfig.ParsedPolicyRule.from(rule));
    }

    @Test
    void testConfigHoldsProfileCorrectly() {
        val profile = ContainerSecurityProfile.builder()
                .policyName("test")
                .build();
        val config = ContainerSecurityPolicyConfig.builder()
                .match(List.of("SERVICE/*"))
                .profile(profile)
                .build();

        assertEquals(List.of("SERVICE/*"), config.getMatch());
        assertEquals("test", config.getProfile().getPolicyName());
    }

    static Stream<Arguments> validRuleTestCases() {
        return Stream.of(
                Arguments.of("*/*",                      Set.copyOf(EnumSet.allOf(JobType.class)), "foo",          null),
                Arguments.of("*/MyApp.*",                Set.copyOf(EnumSet.allOf(JobType.class)), "MyApp123",     "OtherApp"),
                Arguments.of("SERVICE/*",                Set.of(JobType.SERVICE),                  "anything",     null),
                Arguments.of("SERVICE/MyApp",            Set.of(JobType.SERVICE),                  "MyApp",        "MyAppXYZ"),
                Arguments.of("COMPUTATION/*",            Set.of(JobType.COMPUTATION),              "any-task-name", null),
                Arguments.of("LOCAL_SERVICE/myService.*", Set.of(JobType.LOCAL_SERVICE),           "myServiceABC", "otherService")
        );
    }

    static Stream<Arguments> invalidRuleTestCases() {
        return Stream.of(
                Arguments.of("INVALID/MyApp"),
                Arguments.of("SERVICE/MyApp/Extra"),
                Arguments.of("")
        );
    }

}
