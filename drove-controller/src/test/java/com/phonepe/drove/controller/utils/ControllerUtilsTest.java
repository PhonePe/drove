/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.controller.utils;

import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.jobexecutor.JobExecutionResult;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.*;
import com.phonepe.drove.models.localservice.LocalServiceSpec;
import com.phonepe.drove.models.task.TaskSpec;
import dev.failsafe.RetryPolicy;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static com.phonepe.drove.controller.utils.ControllerUtils.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 *
 */
class ControllerUtilsTest {
    @Test
    void waitForStateSuccess() {
        assertEquals(StateCheckStatus.MATCH,
                     ControllerUtils.waitForState(
                             () -> StateCheckStatus.MATCH,
                             RetryPolicy.<StateCheckStatus>builder().withMaxAttempts(1).build()));
    }

    @Test
    void waitForStateFailure() {
        assertThrows(RuntimeException.class,
                     () -> ControllerUtils.waitForState(
                             () -> {
                                 throw new RuntimeException("Test");
                             },
                             RetryPolicy.<StateCheckStatus>builder().withMaxAttempts(1).build()));
    }

    @Test
    void testHostLevelInstance() {
        assertFalse(ControllerUtils.isHostLevelDeployable(new OnePerHostPlacementPolicy()));
        assertFalse(ControllerUtils.isHostLevelDeployable(new MaxNPerHostPlacementPolicy(2)));
        assertFalse(ControllerUtils.isHostLevelDeployable(new MatchTagPlacementPolicy("aa")));
        assertFalse(ControllerUtils.isHostLevelDeployable(new NoTagPlacementPolicy()));
        assertFalse(ControllerUtils.isHostLevelDeployable(new RuleBasedPlacementPolicy("", RuleBasedPlacementPolicy.RuleType.HOPE)));
        assertFalse(ControllerUtils.isHostLevelDeployable(new AnyPlacementPolicy()));
        assertFalse(ControllerUtils.isHostLevelDeployable(new CompositePlacementPolicy(List.of(new AnyPlacementPolicy(),
                                                                                               new MatchTagPlacementPolicy(
                                                                                                       "Aa")),
                                                                                       CompositePlacementPolicy.CombinerType.AND)));
        assertTrue(ControllerUtils.isHostLevelDeployable(new LocalPlacementPolicy(true)));
        assertFalse(ControllerUtils.isHostLevelDeployable(new LocalPlacementPolicy(false)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testErrorMessage() {
        val res = (JobExecutionResult<Boolean>) mock(JobExecutionResult.class);
        {
            when(res.getFailure()).thenReturn(null);
            assertEquals("Execution failed", errorMessage(res));
        }
        reset(res);
        {
            when(res.getFailure()).thenReturn(new Exception("test error"));
            assertEquals("Execution of jobs failed with error: test error", errorMessage(res));
        }
    }

    @Test
    void testComputeEffectivePlacementPolicy() {
        // 1. ApplicationSpec with AnyPlacementPolicy -> Should append NoTag
        {
            ApplicationSpec appSpec = ControllerTestUtils.appSpec().withPlacementPolicy(new AnyPlacementPolicy());
            PlacementPolicy policy = ControllerUtils.computeEffectivePlacementPolicy(appSpec);
            CompositePlacementPolicy composite = assertInstanceOf(CompositePlacementPolicy.class, policy);
            assertEquals(CompositePlacementPolicy.CombinerType.AND, composite.getCombiner());
            assertEquals(2, composite.getPolicies().size());
            assertTrue(composite.getPolicies().stream().anyMatch(AnyPlacementPolicy.class::isInstance));
            assertTrue(composite.getPolicies().stream().anyMatch(NoTagPlacementPolicy.class::isInstance));
        }

        // 2. ApplicationSpec with MatchTagPlacementPolicy -> Should return as is
        {
            ApplicationSpec appSpec = ControllerTestUtils.appSpec().withPlacementPolicy(new MatchTagPlacementPolicy("tag"));
            PlacementPolicy policy = ControllerUtils.computeEffectivePlacementPolicy(appSpec);
            assertInstanceOf(MatchTagPlacementPolicy.class, policy);
            assertEquals("tag", ((MatchTagPlacementPolicy) policy).getTag());
        }

        // 3. TaskSpec with AnyPlacementPolicy -> Should append NoTag
        {
            TaskSpec taskSpec = ControllerTestUtils.taskSpec().withPlacementPolicy(new AnyPlacementPolicy());
            PlacementPolicy policy = ControllerUtils.computeEffectivePlacementPolicy(taskSpec);
            CompositePlacementPolicy composite = assertInstanceOf(CompositePlacementPolicy.class, policy);
            assertEquals(CompositePlacementPolicy.CombinerType.AND, composite.getCombiner());
            assertEquals(2, composite.getPolicies().size());
            assertTrue(composite.getPolicies().stream().anyMatch(AnyPlacementPolicy.class::isInstance));
            assertTrue(composite.getPolicies().stream().anyMatch(NoTagPlacementPolicy.class::isInstance));
        }

        // 4. LocalServiceSpec with AnyPlacementPolicy -> Should return as is (no NoTag appended)
        {
            LocalServiceSpec localServiceSpec = ControllerTestUtils.localServiceSpec().withPlacementPolicy(new AnyPlacementPolicy());
            PlacementPolicy policy = ControllerUtils.computeEffectivePlacementPolicy(localServiceSpec);
            assertInstanceOf(AnyPlacementPolicy.class, policy);
        }

        // 5. LocalServiceSpec with MatchTagPlacementPolicy -> Should return as is
        {
            LocalServiceSpec localServiceSpec = ControllerTestUtils.localServiceSpec().withPlacementPolicy(new MatchTagPlacementPolicy("tag"));
            PlacementPolicy policy = ControllerUtils.computeEffectivePlacementPolicy(localServiceSpec);
            assertInstanceOf(MatchTagPlacementPolicy.class, policy);
            assertEquals("tag", ((MatchTagPlacementPolicy) policy).getTag());
        }
    }

    @Test
    void testMaxStartTimeoutForApplicationSpec() {
        val spec = ControllerTestUtils.appSpec();
        // Docker pull timeout: 100s = 100_000ms
        // Healthcheck: initialDelay=0s, attempts=3, timeout=1s, interval=3s => 0 + 3*(1000+3000) = 12_000
        // Readiness: initialDelay=1s, attempts=3, timeout=1s, interval=3s => 1000 + 3*(1000+3000) = 13_000
        // Total: 100_000 + 12_000 + 13_000 = 125_000
        assertEquals(125_000, maxStartTimeout(spec));
    }

    @Test
    void testMaxStartTimeoutForApplicationSpecWithCustomPullTimeout() {
        val spec = ControllerTestUtils.appSpec()
                .withExecutable(new DockerCoordinates("test:latest", Duration.minutes(5)));
        // Docker pull timeout: 5min = 300_000ms
        // Healthcheck + Readiness = 25_000ms (same as above)
        assertEquals(325_000, maxStartTimeout(spec));
    }

    @Test
    void testMaxStartTimeoutForApplicationSpecWithDefaultPullTimeout() {
        val spec = ControllerTestUtils.appSpec()
                .withExecutable(new DockerCoordinates("test:latest", null));
        // Docker pull timeout: DEFAULT_PULL_TIMEOUT = 15min = 900_000ms
        // Healthcheck + Readiness = 25_000ms
        assertEquals(925_000, maxStartTimeout(spec));
    }

    @Test
    void testMaxStartTimeoutForApplicationSpecWithCustomChecks() {
        val healthcheck = new CheckSpec(
                new HTTPCheckModeSpec(Protocol.HTTP, "main", "/health", HTTPVerb.GET,
                        Collections.singleton(200), "", Duration.seconds(1), false),
                Duration.seconds(5),   // timeout
                Duration.seconds(10),  // interval
                5,                     // attempts
                Duration.seconds(30)); // initialDelay
        val readiness = new CheckSpec(
                new HTTPCheckModeSpec(Protocol.HTTP, "main", "/ready", HTTPVerb.GET,
                        Collections.singleton(200), "", Duration.seconds(1), false),
                Duration.seconds(3),   // timeout
                Duration.seconds(5),   // interval
                10,                    // attempts
                Duration.seconds(15)); // initialDelay
        val spec = ControllerTestUtils.appSpec()
                .withHealthcheck(healthcheck)
                .withReadiness(readiness);
        // Docker pull timeout: 100s = 100_000ms
        // Healthcheck: 30_000 + 5*(5_000+10_000) = 30_000 + 75_000 = 105_000
        // Readiness: 15_000 + 10*(3_000+5_000) = 15_000 + 80_000 = 95_000
        // Total: 100_000 + 105_000 + 95_000 = 300_000
        assertEquals(300_000, maxStartTimeout(spec));
    }

    @Test
    void testMaxStartTimeoutForTaskSpec() {
        val spec = ControllerTestUtils.taskSpec();
        // Docker pull timeout: 100s = 100_000ms
        // Tasks have no healthcheck/readiness => adds 0
        assertEquals(100_000, maxStartTimeout(spec));
    }

    @Test
    void testMaxStartTimeoutForLocalServiceSpec() {
        val spec = ControllerTestUtils.localServiceSpec();
        // Docker pull timeout: 100s = 100_000ms
        // Healthcheck: initialDelay=0s, attempts=3, timeout=1s, interval=3s => 0 + 3*(1000+3000) = 12_000
        // Readiness: initialDelay=1s, attempts=3, timeout=1s, interval=3s => 1000 + 3*(1000+3000) = 13_000
        // Total: 100_000 + 12_000 + 13_000 = 125_000
        assertEquals(125_000, maxStartTimeout(spec));
    }

    @Test
    void testMaxStopTimeoutForApplicationSpecWithoutPreShutdown() {
        val spec = ControllerTestUtils.appSpec();
        // preShutdown is null => 0
        assertEquals(0, maxStopTimeout(spec));
    }

    @Test
    void testMaxStopTimeoutForApplicationSpecWithPreShutdown() {
        val spec = ControllerTestUtils.appSpec()
                .withPreShutdown(new PreShutdownSpec(List.of(), Duration.seconds(30)));
        assertEquals(30_000, maxStopTimeout(spec));
    }

    @Test
    void testMaxStopTimeoutForApplicationSpecWithDefaultPreShutdown() {
        val spec = ControllerTestUtils.appSpec()
                .withPreShutdown(PreShutdownSpec.DEFAULT);
        assertEquals(0, maxStopTimeout(spec));
    }

    @Test
    void testMaxStopTimeoutForTaskSpec() {
        val spec = ControllerTestUtils.taskSpec();
        // Tasks always return 0
        assertEquals(0, maxStopTimeout(spec));
    }

    @Test
    void testMaxStopTimeoutForLocalServiceSpecWithoutPreShutdown() {
        val spec = ControllerTestUtils.localServiceSpec();
        // preShutdown is null => 0
        assertEquals(0, maxStopTimeout(spec));
    }

    @Test
    void testMaxStopTimeoutForLocalServiceSpecWithPreShutdown() {
        val spec = ControllerTestUtils.localServiceSpec()
                .withPreShutdown(new PreShutdownSpec(List.of(), Duration.minutes(2)));
        assertEquals(120_000, maxStopTimeout(spec));
    }

    @Test
    void testWaitTimeWithNullPreShutdownSpec() {
        assertEquals(0, waitTime(null));
    }

    @Test
    void testWaitTimeWithNullWaitBeforeKill() {
        val ps = new PreShutdownSpec(List.of(), null);
        assertEquals(0, waitTime(ps));
    }

    @Test
    void testWaitTimeWithValidPreShutdownSpec() {
        val ps = new PreShutdownSpec(List.of(), Duration.seconds(45));
        assertEquals(45_000, waitTime(ps));
    }

    @Test
    void testWaitTimeWithZeroDuration() {
        val ps = new PreShutdownSpec(List.of(), Duration.seconds(0));
        assertEquals(0, waitTime(ps));
    }
}
