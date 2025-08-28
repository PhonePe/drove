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

import com.phonepe.drove.jobexecutor.JobExecutionResult;
import com.phonepe.drove.models.application.placement.policies.*;
import dev.failsafe.RetryPolicy;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.phonepe.drove.controller.utils.ControllerUtils.errorMessage;
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
}