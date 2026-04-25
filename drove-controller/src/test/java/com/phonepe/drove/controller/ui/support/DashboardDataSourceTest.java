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

package com.phonepe.drove.controller.ui.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.common.discovery.leadership.LeadershipObserver;
import com.phonepe.drove.controller.engine.ApplicationLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.LocalServiceLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.TaskEngine;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.ClusterStateDB;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.application.requirements.ResourceRequirementVisitor;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.info.nodedata.NodeDataVisitor;
import com.phonepe.drove.models.info.resources.available.AvailableCPU;
import com.phonepe.drove.models.info.resources.available.AvailableMemory;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.localservice.ActivationState;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import com.phonepe.drove.models.localservice.LocalServiceSpec;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskState;

import org.junit.jupiter.api.Test;

import lombok.val;

class DashboardDataSourceTest {

    private static final Duration TEST_REFRESH_INTERVAL = Duration.ofMillis(100);

    @Test
    void testCurrentReturnsEmptyWhenNoDataCached() {
        val dataSource = createDataSource();
        val result = dataSource.current();
        assertTrue(result.isEmpty());
    }

    @Test
    void testCurrentReturnsDataAfterRefresh() throws Exception {
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.isLeader()).thenReturn(true);

        val applicationStateDB = mock(ApplicationStateDB.class);
        when(applicationStateDB.applications(anyInt(), anyInt())).thenReturn(List.of());

        val taskEngine = mock(TaskEngine.class);
        when(taskEngine.tasks(any())).thenReturn(List.of());

        val localServiceStateDB = mock(LocalServiceStateDB.class);
        when(localServiceStateDB.services(anyInt(), anyInt())).thenReturn(List.of());

        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        when(clusterResourcesDB.currentSnapshot(anyBoolean())).thenReturn(List.of());

        val appEngine = mock(ApplicationLifecycleManagementEngine.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val localServiceEngine = mock(LocalServiceLifecycleManagementEngine.class);
        val leadershipObserver = mock(LeadershipObserver.class);
        val clusterStateDB = mock(ClusterStateDB.class);

        val dataSource = new DashboardDataSource(
                applicationStateDB,
                taskEngine,
                localServiceStateDB,
                appEngine,
                clusterResourcesDB,
                instanceInfoDB,
                localServiceEngine,
                leadershipEnsurer,
                leadershipObserver,
                clusterStateDB,
                TEST_REFRESH_INTERVAL);

        CommonTestUtils.waitUntil(() -> dataSource.current().isPresent());

        val result = dataSource.current();
        assertTrue(result.isPresent());
        assertNotNull(result.get().getClusterSummary());
        assertNotNull(result.get().getAppStats());
        assertNotNull(result.get().getTaskStats());
        assertNotNull(result.get().getServiceStats());
        assertNotNull(result.get().getExecutorStats());
    }

    @Test
    void testRefreshSkippedWhenNotLeader() throws Exception {
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.isLeader()).thenReturn(false);

        val applicationStateDB = mock(ApplicationStateDB.class);
        val taskEngine = mock(TaskEngine.class);
        val localServiceStateDB = mock(LocalServiceStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val appEngine = mock(ApplicationLifecycleManagementEngine.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val localServiceEngine = mock(LocalServiceLifecycleManagementEngine.class);
        val leadershipObserver = mock(LeadershipObserver.class);
        val clusterStateDB = mock(ClusterStateDB.class);

        val dataSource = new DashboardDataSource(
                applicationStateDB,
                taskEngine,
                localServiceStateDB,
                appEngine,
                clusterResourcesDB,
                instanceInfoDB,
                localServiceEngine,
                leadershipEnsurer,
                leadershipObserver,
                clusterStateDB,
                TEST_REFRESH_INTERVAL);

        CommonTestUtils.delay(TEST_REFRESH_INTERVAL.plus(Duration.ofMillis(50)));

        verify(applicationStateDB, never()).applications(anyInt(), anyInt());
        verify(taskEngine, never()).tasks(any());
        assertTrue(dataSource.current().isEmpty());
    }

    @Test
    void testAppStatsComputationWithApplications() throws Exception {
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.isLeader()).thenReturn(true);

        val appInfo = createMockApplicationInfo("app1", "TestApp");
        val applicationStateDB = mock(ApplicationStateDB.class);
        when(applicationStateDB.applications(anyInt(), anyInt())).thenReturn(List.of(appInfo));

        val appEngine = mock(ApplicationLifecycleManagementEngine.class);
        when(appEngine.currentState(anyString())).thenReturn(Optional.of(ApplicationState.RUNNING));

        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        when(instanceInfoDB.healthyInstances(anyList())).thenReturn(Map.of("app1", List.of(mock(InstanceInfo.class))));

        val taskEngine = mock(TaskEngine.class);
        when(taskEngine.tasks(any())).thenReturn(List.of());

        val localServiceStateDB = mock(LocalServiceStateDB.class);
        when(localServiceStateDB.services(anyInt(), anyInt())).thenReturn(List.of());

        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        when(clusterResourcesDB.currentSnapshot(anyBoolean())).thenReturn(List.of());

        val localServiceEngine = mock(LocalServiceLifecycleManagementEngine.class);
        val leadershipObserver = mock(LeadershipObserver.class);
        val clusterStateDB = mock(ClusterStateDB.class);

        val dataSource = new DashboardDataSource(
                applicationStateDB,
                taskEngine,
                localServiceStateDB,
                appEngine,
                clusterResourcesDB,
                instanceInfoDB,
                localServiceEngine,
                leadershipEnsurer,
                leadershipObserver,
                clusterStateDB,
                TEST_REFRESH_INTERVAL);

        CommonTestUtils.waitUntil(() -> dataSource.current().isPresent());

        val result = dataSource.current();
        assertTrue(result.isPresent());
        assertNotNull(result.get().getAppStats());
        assertEquals(1, result.get().getAppStats().getTotalHealthyInstances());
        assertFalse(result.get().getAppStats().getAppCountByState().isEmpty());
    }

    @Test
    void testTaskStatsComputationWithTasks() throws Exception {
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.isLeader()).thenReturn(true);

        val taskInfo = createMockTaskInfo("task1", TaskState.RUNNING);
        val taskEngine = mock(TaskEngine.class);
        when(taskEngine.tasks(any())).thenReturn(List.of(taskInfo));

        val applicationStateDB = mock(ApplicationStateDB.class);
        when(applicationStateDB.applications(anyInt(), anyInt())).thenReturn(List.of());

        val localServiceStateDB = mock(LocalServiceStateDB.class);
        when(localServiceStateDB.services(anyInt(), anyInt())).thenReturn(List.of());

        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        when(clusterResourcesDB.currentSnapshot(anyBoolean())).thenReturn(List.of());

        val appEngine = mock(ApplicationLifecycleManagementEngine.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val localServiceEngine = mock(LocalServiceLifecycleManagementEngine.class);
        val leadershipObserver = mock(LeadershipObserver.class);
        val clusterStateDB = mock(ClusterStateDB.class);

        val dataSource = new DashboardDataSource(
                applicationStateDB,
                taskEngine,
                localServiceStateDB,
                appEngine,
                clusterResourcesDB,
                instanceInfoDB,
                localServiceEngine,
                leadershipEnsurer,
                leadershipObserver,
                clusterStateDB,
                TEST_REFRESH_INTERVAL);

        CommonTestUtils.waitUntil(() -> dataSource.current().isPresent());

        val result = dataSource.current();
        assertTrue(result.isPresent());
        assertNotNull(result.get().getTaskStats());
        assertFalse(result.get().getTaskStats().getTaskCountByState().isEmpty());
        assertEquals(1, result.get().getTaskStats().getTaskCountByState().get(TaskState.RUNNING));
    }

    @Test
    void testServiceStatsComputationWithServices() throws Exception {
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.isLeader()).thenReturn(true);

        val serviceInfo = createMockLocalServiceInfo("service1", "TestService");
        val localServiceStateDB = mock(LocalServiceStateDB.class);
        when(localServiceStateDB.services(anyInt(), anyInt())).thenReturn(List.of(serviceInfo));
        when(localServiceStateDB.instances(anyString(), any(), anyBoolean())).thenReturn(List.of());

        val localServiceEngine = mock(LocalServiceLifecycleManagementEngine.class);
        when(localServiceEngine.currentState(anyString())).thenReturn(Optional.of(LocalServiceState.ACTIVE));

        val applicationStateDB = mock(ApplicationStateDB.class);
        when(applicationStateDB.applications(anyInt(), anyInt())).thenReturn(List.of());

        val taskEngine = mock(TaskEngine.class);
        when(taskEngine.tasks(any())).thenReturn(List.of());

        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        when(clusterResourcesDB.currentSnapshot(anyBoolean())).thenReturn(List.of());

        val appEngine = mock(ApplicationLifecycleManagementEngine.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val leadershipObserver = mock(LeadershipObserver.class);
        val clusterStateDB = mock(ClusterStateDB.class);

        val dataSource = new DashboardDataSource(
                applicationStateDB,
                taskEngine,
                localServiceStateDB,
                appEngine,
                clusterResourcesDB,
                instanceInfoDB,
                localServiceEngine,
                leadershipEnsurer,
                leadershipObserver,
                clusterStateDB,
                TEST_REFRESH_INTERVAL);

        CommonTestUtils.waitUntil(() -> dataSource.current().isPresent());

        val result = dataSource.current();
        assertTrue(result.isPresent());
        assertNotNull(result.get().getServiceStats());
        assertFalse(result.get().getServiceStats().getServiceCountByState().isEmpty());
    }

    @Test
    void testExecutorStatsComputationWithExecutors() throws Exception {
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.isLeader()).thenReturn(true);

        val executor = createMockExecutorHostInfo("executor1");
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        when(clusterResourcesDB.currentSnapshot(anyBoolean())).thenReturn(List.of(executor));

        val applicationStateDB = mock(ApplicationStateDB.class);
        when(applicationStateDB.applications(anyInt(), anyInt())).thenReturn(List.of());

        val taskEngine = mock(TaskEngine.class);
        when(taskEngine.tasks(any())).thenReturn(List.of());

        val localServiceStateDB = mock(LocalServiceStateDB.class);
        when(localServiceStateDB.services(anyInt(), anyInt())).thenReturn(List.of());

        val appEngine = mock(ApplicationLifecycleManagementEngine.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val localServiceEngine = mock(LocalServiceLifecycleManagementEngine.class);
        val leadershipObserver = mock(LeadershipObserver.class);
        val clusterStateDB = mock(ClusterStateDB.class);

        val dataSource = new DashboardDataSource(
                applicationStateDB,
                taskEngine,
                localServiceStateDB,
                appEngine,
                clusterResourcesDB,
                instanceInfoDB,
                localServiceEngine,
                leadershipEnsurer,
                leadershipObserver,
                clusterStateDB,
                TEST_REFRESH_INTERVAL);

        CommonTestUtils.waitUntil(() -> dataSource.current().isPresent());

        val result = dataSource.current();
        assertTrue(result.isPresent());
        assertNotNull(result.get().getExecutorStats());
        assertNotNull(result.get().getExecutorStats().getExecutorCountByState());
        assertNotNull(result.get().getExecutorStats().getUtilization());
    }

    @Test
    void testExecutorUtilizationCalculation() throws Exception {
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.isLeader()).thenReturn(true);

        val executor1 = createMockExecutorWithUtilization("executor1", 4, 2, 8192, 2048);
        val executor2 = createMockExecutorWithUtilization("executor2", 4, 1, 8192, 4096);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        when(clusterResourcesDB.currentSnapshot(anyBoolean())).thenReturn(List.of(executor1, executor2));

        val applicationStateDB = mock(ApplicationStateDB.class);
        when(applicationStateDB.applications(anyInt(), anyInt())).thenReturn(List.of());

        val taskEngine = mock(TaskEngine.class);
        when(taskEngine.tasks(any())).thenReturn(List.of());

        val localServiceStateDB = mock(LocalServiceStateDB.class);
        when(localServiceStateDB.services(anyInt(), anyInt())).thenReturn(List.of());

        val appEngine = mock(ApplicationLifecycleManagementEngine.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val localServiceEngine = mock(LocalServiceLifecycleManagementEngine.class);
        val leadershipObserver = mock(LeadershipObserver.class);
        val clusterStateDB = mock(ClusterStateDB.class);

        val dataSource = new DashboardDataSource(
                applicationStateDB,
                taskEngine,
                localServiceStateDB,
                appEngine,
                clusterResourcesDB,
                instanceInfoDB,
                localServiceEngine,
                leadershipEnsurer,
                leadershipObserver,
                clusterStateDB,
                TEST_REFRESH_INTERVAL);

        CommonTestUtils.waitUntil(() -> dataSource.current().isPresent());

        val result = dataSource.current();
        assertTrue(result.isPresent());
        val utilization = result.get().getExecutorStats().getUtilization();
        assertNotNull(utilization);
        assertTrue(utilization.getAverageUtilization() > 0);
        assertTrue(utilization.getHighestUtilization() >= utilization.getLowestUtilization());
    }

    @Test
    void testServiceStatsWithHealthyInstances() throws Exception {
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.isLeader()).thenReturn(true);

        val serviceInfo = createMockLocalServiceInfo("service1", "TestService");
        val healthyInstance = createMockLocalServiceInstanceInfo("instance1", "executor1", LocalServiceInstanceState.HEALTHY);
        val localServiceStateDB = mock(LocalServiceStateDB.class);
        when(localServiceStateDB.services(anyInt(), anyInt())).thenReturn(List.of(serviceInfo));
        when(localServiceStateDB.instances(anyString(), any(), anyBoolean())).thenReturn(List.of(healthyInstance));

        val localServiceEngine = mock(LocalServiceLifecycleManagementEngine.class);
        when(localServiceEngine.currentState(anyString())).thenReturn(Optional.of(LocalServiceState.ACTIVE));

        val applicationStateDB = mock(ApplicationStateDB.class);
        when(applicationStateDB.applications(anyInt(), anyInt())).thenReturn(List.of());

        val taskEngine = mock(TaskEngine.class);
        when(taskEngine.tasks(any())).thenReturn(List.of());

        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        when(clusterResourcesDB.currentSnapshot(anyBoolean())).thenReturn(List.of());

        val appEngine = mock(ApplicationLifecycleManagementEngine.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val leadershipObserver = mock(LeadershipObserver.class);
        val clusterStateDB = mock(ClusterStateDB.class);

        val dataSource = new DashboardDataSource(
                applicationStateDB,
                taskEngine,
                localServiceStateDB,
                appEngine,
                clusterResourcesDB,
                instanceInfoDB,
                localServiceEngine,
                leadershipEnsurer,
                leadershipObserver,
                clusterStateDB,
                TEST_REFRESH_INTERVAL);

        CommonTestUtils.waitUntil(() -> dataSource.current().isPresent());

        val result = dataSource.current();
        assertTrue(result.isPresent());
        assertEquals(1, result.get().getServiceStats().getTotalHealthyInstances());
    }

    @Test
    void testTopAppsLimitedTo10() throws Exception {
        val applications = new ArrayList<ApplicationInfo>();
        for (int i = 0; i < 15; i++) {
            applications.add(createMockApplicationInfo("app" + i, "App" + i));
        }

        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.isLeader()).thenReturn(true);

        val applicationStateDB = mock(ApplicationStateDB.class);
        when(applicationStateDB.applications(anyInt(), anyInt())).thenReturn(applications);

        val appEngine = mock(ApplicationLifecycleManagementEngine.class);
        when(appEngine.currentState(anyString())).thenReturn(Optional.of(ApplicationState.RUNNING));

        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        when(instanceInfoDB.healthyInstances(anyList())).thenReturn(Map.of());

        val taskEngine = mock(TaskEngine.class);
        when(taskEngine.tasks(any())).thenReturn(List.of());

        val localServiceStateDB = mock(LocalServiceStateDB.class);
        when(localServiceStateDB.services(anyInt(), anyInt())).thenReturn(List.of());

        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        when(clusterResourcesDB.currentSnapshot(anyBoolean())).thenReturn(List.of());

        val localServiceEngine = mock(LocalServiceLifecycleManagementEngine.class);
        val leadershipObserver = mock(LeadershipObserver.class);
        val clusterStateDB = mock(ClusterStateDB.class);

        val dataSource = new DashboardDataSource(
                applicationStateDB,
                taskEngine,
                localServiceStateDB,
                appEngine,
                clusterResourcesDB,
                instanceInfoDB,
                localServiceEngine,
                leadershipEnsurer,
                leadershipObserver,
                clusterStateDB,
                TEST_REFRESH_INTERVAL);

        CommonTestUtils.waitUntil(() -> dataSource.current().isPresent());

        val result = dataSource.current();
        assertTrue(result.isPresent());
        assertTrue(result.get().getAppStats().getTopApps().size() <= 10);
    }

    @Test
    void testTopTasksLimitedTo5() throws Exception {
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.isLeader()).thenReturn(true);

        val tasks = new ArrayList<TaskInfo>();
        for (int i = 0; i < 10; i++) {
            tasks.add(createMockTaskInfo("task" + i, TaskState.RUNNING));
        }

        val taskEngine = mock(TaskEngine.class);
        when(taskEngine.tasks(any())).thenReturn(tasks);

        val applicationStateDB = mock(ApplicationStateDB.class);
        when(applicationStateDB.applications(anyInt(), anyInt())).thenReturn(List.of());

        val localServiceStateDB = mock(LocalServiceStateDB.class);
        when(localServiceStateDB.services(anyInt(), anyInt())).thenReturn(List.of());

        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        when(clusterResourcesDB.currentSnapshot(anyBoolean())).thenReturn(List.of());

        val appEngine = mock(ApplicationLifecycleManagementEngine.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val localServiceEngine = mock(LocalServiceLifecycleManagementEngine.class);
        val leadershipObserver = mock(LeadershipObserver.class);
        val clusterStateDB = mock(ClusterStateDB.class);

        val dataSource = new DashboardDataSource(
                applicationStateDB,
                taskEngine,
                localServiceStateDB,
                appEngine,
                clusterResourcesDB,
                instanceInfoDB,
                localServiceEngine,
                leadershipEnsurer,
                leadershipObserver,
                clusterStateDB,
                TEST_REFRESH_INTERVAL);

        CommonTestUtils.waitUntil(() -> dataSource.current().isPresent());

        val result = dataSource.current();
        assertTrue(result.isPresent());
        assertTrue(result.get().getTaskStats().getTopTasks().size() <= 5);
    }

    @Test
    void testConcurrentAccessToCurrentMethod() throws Exception {
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.isLeader()).thenReturn(true);

        val applicationStateDB = mock(ApplicationStateDB.class);
        when(applicationStateDB.applications(anyInt(), anyInt())).thenReturn(List.of());

        val taskEngine = mock(TaskEngine.class);
        when(taskEngine.tasks(any())).thenReturn(List.of());

        val localServiceStateDB = mock(LocalServiceStateDB.class);
        when(localServiceStateDB.services(anyInt(), anyInt())).thenReturn(List.of());

        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        when(clusterResourcesDB.currentSnapshot(anyBoolean())).thenReturn(List.of());

        val appEngine = mock(ApplicationLifecycleManagementEngine.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val localServiceEngine = mock(LocalServiceLifecycleManagementEngine.class);
        val leadershipObserver = mock(LeadershipObserver.class);
        val clusterStateDB = mock(ClusterStateDB.class);

        val dataSource = new DashboardDataSource(
                applicationStateDB,
                taskEngine,
                localServiceStateDB,
                appEngine,
                clusterResourcesDB,
                instanceInfoDB,
                localServiceEngine,
                leadershipEnsurer,
                leadershipObserver,
                clusterStateDB,
                TEST_REFRESH_INTERVAL);

        Thread.sleep(TEST_REFRESH_INTERVAL.toMillis() + 50);

        val results = new ArrayList<Boolean>();
        val threads = new ArrayList<Thread>();
        for (int i = 0; i < 5; i++) {
            val thread = new Thread(() -> {
                val result = dataSource.current();
                results.add(result.isPresent());
            });
            threads.add(thread);
            thread.start();
        }

        for (val thread : threads) {
            thread.join();
        }

        assertEquals(5, results.size());
        assertTrue(results.stream().allMatch(r -> r));
    }

    private DashboardDataSource createDataSource() {
        val applicationStateDB = mock(ApplicationStateDB.class);
        val taskEngine = mock(TaskEngine.class);
        val localServiceStateDB = mock(LocalServiceStateDB.class);
        val appEngine = mock(ApplicationLifecycleManagementEngine.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val localServiceEngine = mock(LocalServiceLifecycleManagementEngine.class);
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        val leadershipObserver = mock(LeadershipObserver.class);
        val clusterStateDB = mock(ClusterStateDB.class);

        return new DashboardDataSource(
                applicationStateDB,
                taskEngine,
                localServiceStateDB,
                appEngine,
                clusterResourcesDB,
                instanceInfoDB,
                localServiceEngine,
                leadershipEnsurer,
                leadershipObserver,
                clusterStateDB);
    }

    private ApplicationInfo createMockApplicationInfo(String appId, String appName) {
        val spec = mock(ApplicationSpec.class);
        when(spec.getName()).thenReturn(appName);

        val appInfo = mock(ApplicationInfo.class);
        when(appInfo.getAppId()).thenReturn(appId);
        when(appInfo.getSpec()).thenReturn(spec);
        when(appInfo.getInstances()).thenReturn(1L);
        return appInfo;
    }

    private TaskInfo createMockTaskInfo(String taskId, TaskState state) {
        val taskInfo = mock(TaskInfo.class);
        when(taskInfo.getTaskId()).thenReturn(taskId);
        when(taskInfo.getState()).thenReturn(state);
        when(taskInfo.getCreated()).thenReturn(new Date());
        return taskInfo;
    }

    private LocalServiceInfo createMockLocalServiceInfo(String serviceId, String serviceName) {
        val spec = mock(LocalServiceSpec.class);
        when(spec.getName()).thenReturn(serviceName);

        val serviceInfo = mock(LocalServiceInfo.class);
        when(serviceInfo.getServiceId()).thenReturn(serviceId);
        when(serviceInfo.getSpec()).thenReturn(spec);
        when(serviceInfo.getActivationState()).thenReturn(ActivationState.ACTIVE);
        return serviceInfo;
    }

    private LocalServiceInstanceInfo createMockLocalServiceInstanceInfo(String instanceId, String executorId, LocalServiceInstanceState state) {
        val instanceInfo = mock(LocalServiceInstanceInfo.class);
        when(instanceInfo.getInstanceId()).thenReturn(instanceId);
        when(instanceInfo.getExecutorId()).thenReturn(executorId);
        when(instanceInfo.getState()).thenReturn(state);
        return instanceInfo;
    }

    private ExecutorHostInfo createMockExecutorHostInfo(String executorId) {
        val availableCPU = AvailableCPU.builder()
                .usedCores(Map.of(0, Set.of(0, 1)))
                .freeCores(Map.of(0, Set.of(2, 3)))
                .build();

        val availableMemory = AvailableMemory.builder()
                .usedMemory(Map.of(0, 2048L))
                .freeMemory(Map.of(0, 6144L))
                .build();

        val resourceSnapshot = ExecutorResourceSnapshot.builder()
                .executorId(executorId)
                .cpus(availableCPU)
                .memory(availableMemory)
                .build();

        val nodeData = mock(ExecutorNodeData.class);
        when(nodeData.getExecutorState()).thenReturn(ExecutorState.ACTIVE);
        when(nodeData.getState()).thenReturn(resourceSnapshot);
        when(nodeData.accept(any())).thenAnswer(invocation -> {
            val visitor = invocation.getArgument(0, NodeDataVisitor.class);
            return visitor.visit(nodeData);
        });

        val executorInfo = mock(ExecutorHostInfo.class);
        when(executorInfo.getNodeData()).thenReturn(nodeData);
        return executorInfo;
    }

    private ExecutorHostInfo createMockExecutorWithUtilization(String executorId, int totalCores, int usedCores, long totalMemory, long usedMemory) {
        val usedCoreSet = new HashSet<Integer>();
        for (int i = 0; i < usedCores; i++) {
            usedCoreSet.add(i);
        }
        val freeCoreSet = new HashSet<Integer>();
        for (int i = usedCores; i < totalCores; i++) {
            freeCoreSet.add(i);
        }

        val availableCPU = AvailableCPU.builder()
                .usedCores(Map.of(0, usedCoreSet))
                .freeCores(Map.of(0, freeCoreSet))
                .build();

        val availableMemory = AvailableMemory.builder()
                .usedMemory(Map.of(0, usedMemory))
                .freeMemory(Map.of(0, totalMemory - usedMemory))
                .build();

        val resourceSnapshot = ExecutorResourceSnapshot.builder()
                .executorId(executorId)
                .cpus(availableCPU)
                .memory(availableMemory)
                .build();

        val nodeData = mock(ExecutorNodeData.class);
        when(nodeData.getExecutorState()).thenReturn(ExecutorState.ACTIVE);
        when(nodeData.getState()).thenReturn(resourceSnapshot);
        when(nodeData.accept(any())).thenAnswer(invocation -> {
            val visitor = invocation.getArgument(0, NodeDataVisitor.class);
            return visitor.visit(nodeData);
        });

        val executorInfo = mock(ExecutorHostInfo.class);
        when(executorInfo.getNodeData()).thenReturn(nodeData);
        return executorInfo;
    }

    @Test
    void testZeroInstanceAppsExcludedFromTopApps() throws Exception {
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.isLeader()).thenReturn(true);

        val app1 = createMockApplicationInfo("app1", "App1");
        when(app1.getInstances()).thenReturn(2L);

        val app2 = createMockApplicationInfo("app2", "App2");
        when(app2.getInstances()).thenReturn(0L);

        val app3 = createMockApplicationInfo("app3", "App3");
        when(app3.getInstances()).thenReturn(1L);

        val applicationStateDB = mock(ApplicationStateDB.class);
        when(applicationStateDB.applications(anyInt(), anyInt())).thenReturn(List.of(app1, app2, app3));

        val appEngine = mock(ApplicationLifecycleManagementEngine.class);
        when(appEngine.currentState(anyString())).thenReturn(Optional.of(ApplicationState.RUNNING));

        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        when(instanceInfoDB.healthyInstances(anyList())).thenReturn(Map.of());

        val taskEngine = mock(TaskEngine.class);
        when(taskEngine.tasks(any())).thenReturn(List.of());

        val localServiceStateDB = mock(LocalServiceStateDB.class);
        when(localServiceStateDB.services(anyInt(), anyInt())).thenReturn(List.of());

        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        when(clusterResourcesDB.currentSnapshot(anyBoolean())).thenReturn(List.of());

        val localServiceEngine = mock(LocalServiceLifecycleManagementEngine.class);
        val leadershipObserver = mock(LeadershipObserver.class);
        val clusterStateDB = mock(ClusterStateDB.class);

        val dataSource = new DashboardDataSource(
                applicationStateDB,
                taskEngine,
                localServiceStateDB,
                appEngine,
                clusterResourcesDB,
                instanceInfoDB,
                localServiceEngine,
                leadershipEnsurer,
                leadershipObserver,
                clusterStateDB,
                TEST_REFRESH_INTERVAL);

        CommonTestUtils.waitUntil(() -> dataSource.current().isPresent());

        val result = dataSource.current();
        assertTrue(result.isPresent());
        val topApps = result.get().getAppStats().getTopApps();
        assertEquals(2, topApps.size());
        assertTrue(topApps.stream().noneMatch(app -> app.getId().equals("app2")));
    }

    @Test
    void testAppScoringWithScaledIntegers() throws Exception {
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.isLeader()).thenReturn(true);

        val app1 = createMockApplicationInfoWithResources("app1", "App1", 2L, 8L, 8192L);
        val app2 = createMockApplicationInfoWithResources("app2", "App2", 1L, 8L, 8192L);
        val app3 = createMockApplicationInfoWithResources("app3", "App3", 1L, 4L, 4096L);

        val applicationStateDB = mock(ApplicationStateDB.class);
        when(applicationStateDB.applications(anyInt(), anyInt())).thenReturn(List.of(app1, app2, app3));

        val appEngine = mock(ApplicationLifecycleManagementEngine.class);
        when(appEngine.currentState(anyString())).thenReturn(Optional.of(ApplicationState.RUNNING));

        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        when(instanceInfoDB.healthyInstances(anyList())).thenReturn(Map.of());

        val taskEngine = mock(TaskEngine.class);
        when(taskEngine.tasks(any())).thenReturn(List.of());

        val localServiceStateDB = mock(LocalServiceStateDB.class);
        when(localServiceStateDB.services(anyInt(), anyInt())).thenReturn(List.of());

        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        when(clusterResourcesDB.currentSnapshot(anyBoolean())).thenReturn(List.of());

        val localServiceEngine = mock(LocalServiceLifecycleManagementEngine.class);
        val leadershipObserver = mock(LeadershipObserver.class);
        val clusterStateDB = mock(ClusterStateDB.class);

        val dataSource = new DashboardDataSource(
                applicationStateDB,
                taskEngine,
                localServiceStateDB,
                appEngine,
                clusterResourcesDB,
                instanceInfoDB,
                localServiceEngine,
                leadershipEnsurer,
                leadershipObserver,
                clusterStateDB,
                TEST_REFRESH_INTERVAL);

        CommonTestUtils.waitUntil(() -> dataSource.current().isPresent());

        val result = dataSource.current();
        assertTrue(result.isPresent());
        val topApps = result.get().getAppStats().getTopApps();
        assertEquals(3, topApps.size());
        assertEquals("app1", topApps.get(0).getId());
        assertEquals("app2", topApps.get(1).getId());
        assertEquals("app3", topApps.get(2).getId());
    }

    private ApplicationInfo createMockApplicationInfoWithResources(String appId,
                                                                   String appName,
                                                                   long instances,
                                                                   long cpuCount,
                                                                   long memoryMB) {
        val cpuRequirement = mock(CPURequirement.class);
        when(cpuRequirement.getCount()).thenReturn(cpuCount);
        when(cpuRequirement.accept(any())).thenAnswer(invocation -> {
            val visitor = invocation.getArgument(0,
                                                 ResourceRequirementVisitor.class);
            return visitor.visit(cpuRequirement);
        });

        val memoryRequirement = mock(MemoryRequirement.class);
        when(memoryRequirement.getSizeInMB()).thenReturn(memoryMB);
        when(memoryRequirement.accept(any())).thenAnswer(invocation -> {
            val visitor = invocation.getArgument(0,
                                                 ResourceRequirementVisitor.class);
            return visitor.visit(memoryRequirement);
        });

        val spec = mock(ApplicationSpec.class);
        when(spec.getName()).thenReturn(appName);
        when(spec.getResources()).thenReturn(List.of(cpuRequirement, memoryRequirement));

        val appInfo = mock(ApplicationInfo.class);
        when(appInfo.getAppId()).thenReturn(appId);
        when(appInfo.getSpec()).thenReturn(spec);
        when(appInfo.getInstances()).thenReturn(instances);
        return appInfo;
    }



    @Test
    void testServiceStatsOnlyIncludesResourceUsingStates() throws Exception {
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.isLeader()).thenReturn(true);

        val activeService = createMockLocalServiceInfoWithResources("service1", "ActiveService", 2L, 4096L);
        val inactiveService = createMockLocalServiceInfoWithResources("service2", "InactiveService", 2L, 4096L);
        val configTestingService = createMockLocalServiceInfoWithResources("service3", "ConfigTestService", 1L, 2048L);

        val localServiceStateDB = mock(LocalServiceStateDB.class);
        when(localServiceStateDB.services(anyInt(), anyInt())).thenReturn(List.of(activeService, inactiveService, configTestingService));
        when(localServiceStateDB.instances(anyString(), any(), anyBoolean())).thenReturn(List.of());

        val localServiceEngine = mock(LocalServiceLifecycleManagementEngine.class);
        when(localServiceEngine.currentState("service1")).thenReturn(Optional.of(LocalServiceState.ACTIVE));
        when(localServiceEngine.currentState("service2")).thenReturn(Optional.of(LocalServiceState.INACTIVE));
        when(localServiceEngine.currentState("service3")).thenReturn(Optional.of(LocalServiceState.CONFIG_TESTING));

        val applicationStateDB = mock(ApplicationStateDB.class);
        when(applicationStateDB.applications(anyInt(), anyInt())).thenReturn(List.of());

        val taskEngine = mock(TaskEngine.class);
        when(taskEngine.tasks(any())).thenReturn(List.of());

        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        when(clusterResourcesDB.currentSnapshot(anyBoolean())).thenReturn(List.of());

        val appEngine = mock(ApplicationLifecycleManagementEngine.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val leadershipObserver = mock(LeadershipObserver.class);
        val clusterStateDB = mock(ClusterStateDB.class);

        val dataSource = new DashboardDataSource(
                applicationStateDB,
                taskEngine,
                localServiceStateDB,
                appEngine,
                clusterResourcesDB,
                instanceInfoDB,
                localServiceEngine,
                leadershipEnsurer,
                leadershipObserver,
                clusterStateDB,
                TEST_REFRESH_INTERVAL);

        CommonTestUtils.waitUntil(() -> dataSource.current().isPresent());

        val result = dataSource.current();
        assertTrue(result.isPresent());
        val topServices = result.get().getServiceStats().getTopServices();
        assertEquals(2, topServices.size());
        assertTrue(topServices.stream().anyMatch(svc -> svc.getId().equals("service1")));
        assertTrue(topServices.stream().anyMatch(svc -> svc.getId().equals("service3")));
        assertFalse(topServices.stream().anyMatch(svc -> svc.getId().equals("service2")));
    }

    @Test
    void testTaskStatsExcludesTerminalTasks() throws Exception {
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.isLeader()).thenReturn(true);

        val runningTask = createMockTaskInfo("task1", TaskState.RUNNING);
        val stoppedTask = createMockTaskInfo("task2", TaskState.STOPPED);
        val lostTask = createMockTaskInfo("task3", TaskState.LOST);
        val pendingTask = createMockTaskInfo("task4", TaskState.PENDING);

        val taskEngine = mock(TaskEngine.class);
        when(taskEngine.tasks(any())).thenReturn(List.of(runningTask, stoppedTask, lostTask, pendingTask));

        val applicationStateDB = mock(ApplicationStateDB.class);
        when(applicationStateDB.applications(anyInt(), anyInt())).thenReturn(List.of());

        val localServiceStateDB = mock(LocalServiceStateDB.class);
        when(localServiceStateDB.services(anyInt(), anyInt())).thenReturn(List.of());

        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        when(clusterResourcesDB.currentSnapshot(anyBoolean())).thenReturn(List.of());

        val appEngine = mock(ApplicationLifecycleManagementEngine.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val localServiceEngine = mock(LocalServiceLifecycleManagementEngine.class);
        val leadershipObserver = mock(LeadershipObserver.class);
        val clusterStateDB = mock(ClusterStateDB.class);

        val dataSource = new DashboardDataSource(
                applicationStateDB,
                taskEngine,
                localServiceStateDB,
                appEngine,
                clusterResourcesDB,
                instanceInfoDB,
                localServiceEngine,
                leadershipEnsurer,
                leadershipObserver,
                clusterStateDB,
                TEST_REFRESH_INTERVAL);

        CommonTestUtils.waitUntil(() -> dataSource.current().isPresent());

        val result = dataSource.current();
        assertTrue(result.isPresent());
        val topTasks = result.get().getTaskStats().getTopTasks();
        assertEquals(2, topTasks.size());
        assertTrue(topTasks.stream().anyMatch(task -> task.getTaskId().equals("task1")));
        assertTrue(topTasks.stream().anyMatch(task -> task.getTaskId().equals("task4")));
        assertFalse(topTasks.stream().anyMatch(task -> task.getTaskId().equals("task2")));
        assertFalse(topTasks.stream().anyMatch(task -> task.getTaskId().equals("task3")));
    }

    private LocalServiceInfo createMockLocalServiceInfoWithResources(String serviceId,
                                                                      String serviceName,
                                                                      long cpuCount,
                                                                      long memoryMB) {
        val cpuRequirement = mock(CPURequirement.class);
        when(cpuRequirement.getCount()).thenReturn(cpuCount);
        when(cpuRequirement.accept(any())).thenAnswer(invocation -> {
            val visitor = invocation.getArgument(0, ResourceRequirementVisitor.class);
            return visitor.visit(cpuRequirement);
        });

        val memoryRequirement = mock(MemoryRequirement.class);
        when(memoryRequirement.getSizeInMB()).thenReturn(memoryMB);
        when(memoryRequirement.accept(any())).thenAnswer(invocation -> {
            val visitor = invocation.getArgument(0, ResourceRequirementVisitor.class);
            return visitor.visit(memoryRequirement);
        });

        val spec = mock(LocalServiceSpec.class);
        when(spec.getName()).thenReturn(serviceName);
        when(spec.getResources()).thenReturn(List.of(cpuRequirement, memoryRequirement));

        val serviceInfo = mock(LocalServiceInfo.class);
        when(serviceInfo.getServiceId()).thenReturn(serviceId);
        when(serviceInfo.getSpec()).thenReturn(spec);
        when(serviceInfo.getActivationState()).thenReturn(ActivationState.ACTIVE);
        return serviceInfo;
    }


}
