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

import static com.phonepe.drove.controller.utils.ControllerUtils.errorMessage;
import static com.phonepe.drove.controller.utils.ControllerUtils.maxStartTimeout;
import static com.phonepe.drove.controller.utils.ControllerUtils.maxStopTimeout;
import static com.phonepe.drove.controller.utils.ControllerUtils.waitTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.jobexecutor.JobExecutionResult;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.PreShutdownSpec;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.AnyPlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.CompositePlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.LocalPlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.MatchTagPlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.MaxNPerHostPlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.NoTagPlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.OnePerHostPlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.RuleBasedPlacementPolicy;
import com.phonepe.drove.models.common.HTTPVerb;
import com.phonepe.drove.models.common.Protocol;
import com.phonepe.drove.models.localservice.LocalServiceSpec;
import com.phonepe.drove.models.task.TaskSpec;
import com.phonepe.drove.models.taskinstance.TaskState;

import org.junit.jupiter.api.Test;

import dev.failsafe.RetryPolicy;
import io.dropwizard.util.Duration;
import lombok.val;

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
    void testToAppSummary() {
        val spec = ControllerTestUtils.appSpec("TEST_APP", 2);
        val appId = ControllerUtils.deployableObjectId(spec);
        val appInfo = new com.phonepe.drove.models.application.ApplicationInfo(
                appId, spec, 5, new java.util.Date(), new java.util.Date());
        val appEngine = mock(com.phonepe.drove.controller.engine.ApplicationLifecycleManagementEngine.class);
        when(appEngine.currentState(appId)).thenReturn(java.util.Optional.of(
                com.phonepe.drove.models.application.ApplicationState.RUNNING));

        val summary = ControllerUtils.toAppSummary(appInfo, appEngine, 3);

        assertEquals(appId, summary.getId());
        assertEquals("TEST_APP", summary.getName());
        assertEquals(5, summary.getRequiredInstances());
        assertEquals(3, summary.getHealthyInstances());
        assertEquals(5, summary.getTotalCPUs());
        assertEquals(2560, summary.getTotalMemory());
        assertEquals(spec.getTags(), summary.getTags());
        assertEquals(com.phonepe.drove.models.application.ApplicationState.RUNNING, summary.getState());
        assertEquals(appInfo.getCreated(), summary.getCreated());
        assertEquals(appInfo.getUpdated(), summary.getUpdated());
    }

    @Test
    void testToAppSummaryWithNoState() {
        val spec = ControllerTestUtils.appSpec("TEST_APP", 1);
        val appId = ControllerUtils.deployableObjectId(spec);
        val appInfo = new com.phonepe.drove.models.application.ApplicationInfo(
                appId, spec, 2, new java.util.Date(), new java.util.Date());
        val appEngine = mock(com.phonepe.drove.controller.engine.ApplicationLifecycleManagementEngine.class);
        when(appEngine.currentState(appId)).thenReturn(java.util.Optional.empty());

        val summary = ControllerUtils.toAppSummary(appInfo, appEngine, 0);

        assertEquals(appId, summary.getId());
        assertEquals("TEST_APP", summary.getName());
        assertEquals(2, summary.getRequiredInstances());
        assertEquals(0, summary.getHealthyInstances());
        assertNull(summary.getState());
    }

    @Test
    void testToLocalServiceSummary() {
        val spec = ControllerTestUtils.localServiceSpec("TEST_SERVICE", 1);
        val serviceId = ControllerUtils.deployableObjectId(spec);
        val serviceInfo = new com.phonepe.drove.models.localservice.LocalServiceInfo(
                serviceId, spec, 3, 
                com.phonepe.drove.models.localservice.ActivationState.ACTIVE,
                new java.util.Date(), new java.util.Date());
        val lsEngine = mock(com.phonepe.drove.controller.engine.LocalServiceLifecycleManagementEngine.class);
        when(lsEngine.currentState(serviceId)).thenReturn(java.util.Optional.of(
                com.phonepe.drove.models.localservice.LocalServiceState.ACTIVE));

        val summary = ControllerUtils.toLocalServiceSummary(serviceInfo, 10, 8, lsEngine);

        assertEquals(serviceId, summary.getId());
        assertEquals("TEST_SERVICE", summary.getName());
        assertEquals(3, summary.getInstancesPerHost());
        assertEquals(8, summary.getHealthyInstances());
        assertEquals(10, summary.getTotalCPUs());
        assertEquals(5120, summary.getTotalMemory());
        assertEquals(spec.getTags(), summary.getTags());
        assertEquals(com.phonepe.drove.models.localservice.ActivationState.ACTIVE, summary.getActivationState());
        assertEquals(com.phonepe.drove.models.localservice.LocalServiceState.ACTIVE, summary.getState());
        assertEquals(serviceInfo.getCreated(), summary.getCreated());
        assertEquals(serviceInfo.getUpdated(), summary.getUpdated());
    }

    @Test
    void testToLocalServiceSummaryWithNoState() {
        val spec = ControllerTestUtils.localServiceSpec("TEST_SERVICE", 1);
        val serviceId = ControllerUtils.deployableObjectId(spec);
        val serviceInfo = new com.phonepe.drove.models.localservice.LocalServiceInfo(
                serviceId, spec, 2,
                com.phonepe.drove.models.localservice.ActivationState.INACTIVE,
                new java.util.Date(), new java.util.Date());
        val lsEngine = mock(com.phonepe.drove.controller.engine.LocalServiceLifecycleManagementEngine.class);
        when(lsEngine.currentState(serviceId)).thenReturn(java.util.Optional.empty());

        val summary = ControllerUtils.toLocalServiceSummary(serviceInfo, 5, 0, lsEngine);

        assertEquals(serviceId, summary.getId());
        assertEquals("TEST_SERVICE", summary.getName());
        assertEquals(2, summary.getInstancesPerHost());
        assertEquals(0, summary.getHealthyInstances());
        assertEquals(com.phonepe.drove.models.localservice.ActivationState.INACTIVE, summary.getActivationState());
        assertNull(summary.getState());
    }

    @Test
    void testComputeClusterSummary() {
        val leadershipObserver = mock(com.phonepe.drove.common.discovery.leadership.LeadershipObserver.class);
        val leaderNode = new com.phonepe.drove.models.info.nodedata.ControllerNodeData(
                "leader-host", 8080, com.phonepe.drove.models.info.nodedata.NodeTransportType.HTTP,
                new java.util.Date(), true);
        when(leadershipObserver.leader()).thenReturn(java.util.Optional.of(leaderNode));

        val clusterStateDB = mock(com.phonepe.drove.controller.statedb.ClusterStateDB.class);
        when(clusterStateDB.currentState()).thenReturn(java.util.Optional.of(
                new com.phonepe.drove.models.common.ClusterStateData(
                        com.phonepe.drove.models.common.ClusterState.NORMAL, new java.util.Date())));

        val appEngine = mock(com.phonepe.drove.controller.engine.ApplicationLifecycleManagementEngine.class);
        when(appEngine.currentState("app1")).thenReturn(java.util.Optional.of(
                com.phonepe.drove.models.application.ApplicationState.RUNNING));
        when(appEngine.currentState("app2")).thenReturn(java.util.Optional.of(
                com.phonepe.drove.models.application.ApplicationState.MONITORING));
        when(appEngine.currentState("app3")).thenReturn(java.util.Optional.of(
                com.phonepe.drove.models.application.ApplicationState.FAILED));

        val lsEngine = mock(com.phonepe.drove.controller.engine.LocalServiceLifecycleManagementEngine.class);
        when(lsEngine.currentState("ls1")).thenReturn(java.util.Optional.of(
                com.phonepe.drove.models.localservice.LocalServiceState.ACTIVE));
        when(lsEngine.currentState("ls2")).thenReturn(java.util.Optional.of(
                com.phonepe.drove.models.localservice.LocalServiceState.DESTROYED));

        val applications = List.of(
                new com.phonepe.drove.models.application.ApplicationInfo(
                        "app1", ControllerTestUtils.appSpec("APP1", 1), 2, 
                        new java.util.Date(), new java.util.Date()),
                new com.phonepe.drove.models.application.ApplicationInfo(
                        "app2", ControllerTestUtils.appSpec("APP2", 1), 3,
                        new java.util.Date(), new java.util.Date()),
                new com.phonepe.drove.models.application.ApplicationInfo(
                        "app3", ControllerTestUtils.appSpec("APP3", 1), 1,
                        new java.util.Date(), new java.util.Date())
        );

        val tasks = List.of(
                ControllerTestUtils.generateTaskInfo(ControllerTestUtils.taskSpec("TASK1", "APP1"), 0, TaskState.RUNNING),
                ControllerTestUtils.generateTaskInfo(ControllerTestUtils.taskSpec("TASK2", "APP1"), 1, TaskState.STOPPED)
        );

        val localServices = List.of(
                new com.phonepe.drove.models.localservice.LocalServiceInfo(
                        "ls1", ControllerTestUtils.localServiceSpec("LS1", 1), 2,
                        com.phonepe.drove.models.localservice.ActivationState.ACTIVE,
                        new java.util.Date(), new java.util.Date()),
                new com.phonepe.drove.models.localservice.LocalServiceInfo(
                        "ls2", ControllerTestUtils.localServiceSpec("LS2", 1), 1,
                        com.phonepe.drove.models.localservice.ActivationState.INACTIVE,
                        new java.util.Date(), new java.util.Date())
        );

        val resourceSummary = new com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB.ClusterResourcesSummary(
                10, 40, 20, 60, 102400, 51200, 153600);

        val summary = ControllerUtils.computeClusterSummary(
                leadershipObserver, clusterStateDB, applications, tasks, localServices,
                appEngine, lsEngine, resourceSummary);

        assertEquals("leader-host:8080", summary.getLeader());
        assertEquals(com.phonepe.drove.models.common.ClusterState.NORMAL, summary.getState());
        assertEquals(10, summary.getNumExecutors());
        assertEquals(3, summary.getNumApplications());
        assertEquals(1, summary.getNumActiveApplications());
        assertEquals(1, summary.getNumActiveTasks());
        assertEquals(2, summary.getNumLocalServices());
        assertEquals(1, summary.getNumActiveLocalServices());
        assertEquals(40, summary.getFreeCores());
        assertEquals(20, summary.getUsedCores());
        assertEquals(60, summary.getTotalCores());
        assertEquals(102400, summary.getFreeMemory());
        assertEquals(51200, summary.getUsedMemory());
        assertEquals(153600, summary.getTotalMemory());
    }

    @Test
    void testComputeClusterSummaryWithNoLeader() {
        val leadershipObserver = mock(com.phonepe.drove.common.discovery.leadership.LeadershipObserver.class);
        when(leadershipObserver.leader()).thenReturn(java.util.Optional.empty());

        val clusterStateDB = mock(com.phonepe.drove.controller.statedb.ClusterStateDB.class);
        when(clusterStateDB.currentState()).thenReturn(java.util.Optional.empty());

        val appEngine = mock(com.phonepe.drove.controller.engine.ApplicationLifecycleManagementEngine.class);
        val lsEngine = mock(com.phonepe.drove.controller.engine.LocalServiceLifecycleManagementEngine.class);

        val resourceSummary = new com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB.ClusterResourcesSummary(
                5, 10, 5, 15, 51200, 25600, 76800);

        val summary = ControllerUtils.computeClusterSummary(
                leadershipObserver, clusterStateDB, List.of(), List.of(), List.of(),
                appEngine, lsEngine, resourceSummary);

        assertEquals("Leader election underway", summary.getLeader());
        assertEquals(com.phonepe.drove.models.common.ClusterState.NORMAL, summary.getState());
        assertEquals(5, summary.getNumExecutors());
        assertEquals(0, summary.getNumApplications());
        assertEquals(0, summary.getNumActiveApplications());
        assertEquals(0, summary.getNumActiveTasks());
        assertEquals(0, summary.getNumLocalServices());
        assertEquals(0, summary.getNumActiveLocalServices());
    }

}
