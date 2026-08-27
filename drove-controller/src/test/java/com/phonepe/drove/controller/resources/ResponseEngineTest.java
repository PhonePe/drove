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

package com.phonepe.drove.controller.resources;

import static com.phonepe.drove.controller.ControllerTestUtils.appSpec;
import static com.phonepe.drove.controller.ControllerTestUtils.executorHost;
import static com.phonepe.drove.controller.ControllerTestUtils.generateInstanceInfo;
import static com.phonepe.drove.controller.ControllerTestUtils.localServiceSpec;
import static com.phonepe.drove.controller.ControllerTestUtils.taskSpec;
import static com.phonepe.drove.models.api.ApiErrorCode.FAILED;
import static com.phonepe.drove.models.api.ApiErrorCode.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import com.codahale.metrics.SharedMetricRegistries;
import com.google.common.collect.Maps;
import com.phonepe.drove.common.discovery.leadership.LeadershipObserver;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.discovery.leadership.ZkLeadershipObserver;
import com.phonepe.drove.controller.engine.ApplicationLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.LocalServiceLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.TaskEngine;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.event.EventStore;
import com.phonepe.drove.controller.event.InMemoryEventStore;
import com.phonepe.drove.controller.managed.BlacklistingManager;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.controller.metrics.ClusterMetricsRegistry;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.ClusterStateDB;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.controller.ui.support.DashboardDataSource;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.controller.utils.EventUtils;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.api.BlacklistOperationResponse;
import com.phonepe.drove.models.api.ClusterSummary;
import com.phonepe.drove.models.api.DashboardData;
import com.phonepe.drove.models.api.GpuResourceInfo;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.application.exposure.ExposureMode;
import com.phonepe.drove.models.application.exposure.ExposureSpec;
import com.phonepe.drove.models.api.ExposedAppInfo;
import com.phonepe.drove.models.common.ClusterState;
import com.phonepe.drove.models.common.ClusterStateData;
import com.phonepe.drove.models.events.events.DroveClusterMaintenanceModeSetEvent;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.localservice.ActivationState;
import com.phonepe.drove.models.localservice.LocalServiceInfo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.val;

/**
 *
 */
class ResponseEngineTest {

    private final LeadershipObserver leadershipObserver = mock(ZkLeadershipObserver.class);
    private final ApplicationLifecycleManagementEngine appEngine = mock(ApplicationLifecycleManagementEngine.class);
    private final ApplicationStateDB applicationStateDB = mock(ApplicationStateDB.class);
    private final ApplicationInstanceInfoDB instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
    private final ClusterStateDB clusterStateDB = mock(ClusterStateDB.class);
    private final ClusterResourcesDB clusterResourcesDB = mock(ClusterResourcesDB.class);
    private final TaskDB taskDB = mock(TaskDB.class);
    private final TaskEngine taskEngine = mock(TaskEngine.class);
    private final LocalServiceLifecycleManagementEngine lsEngine = mock(LocalServiceLifecycleManagementEngine.class);
    private final LocalServiceStateDB localServiceStateDB = mock(LocalServiceStateDB.class);
    private final EventStore eventStore = mock(EventStore.class);
    private final ControllerCommunicator communicator = mock(ControllerCommunicator.class);
    private final DroveEventBus eventBus = mock(DroveEventBus.class);
    private final BlacklistingManager blacklistingManager = mock(BlacklistingManager.class);
    private final DashboardDataSource dashboardDataSource = mock(DashboardDataSource.class);
    private final ResponseEngine re = new ResponseEngine(leadershipObserver,
                                                         appEngine,
                                                         applicationStateDB,
                                                         instanceInfoDB,
                                                         taskDB,
                                                         taskEngine,
                                                         lsEngine,
                                                         localServiceStateDB,
                                                         clusterStateDB,
                                                         clusterResourcesDB,
                                                         eventStore,
                                                         communicator,
                                                         eventBus,
                                                         blacklistingManager,
                                                         dashboardDataSource);

    @AfterEach
    void resetMocks() {
        reset(leadershipObserver,
              appEngine,
              applicationStateDB,
              instanceInfoDB,
              clusterResourcesDB,
              clusterResourcesDB,
              taskDB,
              lsEngine,
              localServiceStateDB,
              eventBus,
              communicator,
              eventBus,
              blacklistingManager);
    }

    @Test
    void testApplications() {
        val rng = new SecureRandom();
        when(applicationStateDB.applications(0, Integer.MAX_VALUE))
                .thenReturn(IntStream.range(0, 100)
                                    .mapToObj(i -> {
                                        return createApp(i, rng.nextInt(100));
                                    })
                                    .toList());

        val res = re.applications(0, Integer.MAX_VALUE);
        assertEquals(SUCCESS, res.getStatus());
        assertEquals("success", res.getMessage());
        assertEquals(100, res.getData().size());
    }

    @Test
    void testApplication() {
        val spec = appSpec();
        val appId = ControllerUtils.deployableObjectId(spec);

        val applicationInfo = new ApplicationInfo(appId, spec, 5, new Date(), new Date());
        when(applicationStateDB.application(appId))
                .thenReturn(Optional.of(applicationInfo));

        val res = re.application(appId);
        assertEquals(SUCCESS, res.getStatus());
        assertEquals("success", res.getMessage());
        assertNotNull(res.getData());
    }

    @Test
    void testApplicationSpec() {
        val spec = appSpec();
        val appId = ControllerUtils.deployableObjectId(spec);

        val applicationInfo = new ApplicationInfo(appId, spec, 5, new Date(), new Date());
        when(applicationStateDB.application(appId))
                .thenReturn(Optional.of(applicationInfo));

        val res = re.applicationSpec(appId);
        assertEquals(SUCCESS, res.getStatus());
        assertEquals("success", res.getMessage());
        assertNotNull(res.getData());
        assertEquals(spec, res.getData());
    }


    @Test
    void testApplicationInstances() {
        val spec = appSpec();
        val appId = ControllerUtils.deployableObjectId(spec);
        val instances = IntStream.rangeClosed(1, 100)
                .mapToObj(i -> generateInstanceInfo(appId, spec, i, InstanceState.STARTING))
                .toList();

        when(instanceInfoDB.activeInstances(appId, 0, Integer.MAX_VALUE)).thenReturn(instances);

        val res = re.applicationInstances(appId, Set.of());
        assertEquals(SUCCESS, res.getStatus());
        assertEquals("success", res.getMessage());
        assertEquals(100, res.getData().size());
        assertEquals(instances, res.getData());
        assertTrue(re.applicationInstances(appId, Set.of(InstanceState.HEALTHY)).getData().isEmpty());
        assertEquals(instances, re.applicationInstances(appId, Set.of(InstanceState.STARTING)).getData());
    }

    @Test
    void testInstanceDetails() {
        val spec = appSpec();
        val appId = ControllerUtils.deployableObjectId(spec);
        val instance = generateInstanceInfo(appId, spec, 1, InstanceState.STARTING);

        final var instanceId = instance.getInstanceId();
        when(instanceInfoDB.instance(appId, instanceId)).thenReturn(Optional.of(instance));

        {
            val res = re.instanceDetails(appId, instanceId);
            assertEquals(SUCCESS, res.getStatus());
            assertEquals("success", res.getMessage());
            assertEquals(instance, res.getData());
        }
        {
            val res = re.instanceDetails(appId, "blah");
            assertEquals(FAILED, res.getStatus());
            assertEquals("No such application instance " + appId + "/blah", res.getMessage());
            assertNull(res.getData());
        }
    }

    @Test
    void testApplicationOldInstances() {
        val spec = appSpec();
        val appId = ControllerUtils.deployableObjectId(spec);
        val instances = IntStream.rangeClosed(1, 100)
                .mapToObj(i -> generateInstanceInfo(appId, spec, i, InstanceState.STOPPED))
                .toList();

        when(instanceInfoDB.oldInstances(appId, 0, Integer.MAX_VALUE)).thenReturn(instances);

        val res = re.applicationOldInstances(appId, 0, Integer.MAX_VALUE);
        assertEquals(SUCCESS, res.getStatus());
        assertEquals("success", res.getMessage());
        assertEquals(100, res.getData().size());
        assertEquals(instances, res.getData());
    }

    @Test
    void testTaskDetails() {
        when(taskDB.task(eq("WRONG_APP"), anyString())).thenReturn(Optional.empty());
        val spec = taskSpec();
        when(taskDB.task(spec.getSourceAppName(), spec.getTaskId()))
                .thenReturn(Optional.of(ControllerTestUtils.generateTaskInfo(spec, 0)));
        {
            val res = re.taskDetails("WRONMG_APP", "T001");
            assertEquals(FAILED, res.getStatus());
        }
        {
            val res = re.taskDetails(spec.getSourceAppName(), spec.getTaskId());
            assertEquals(SUCCESS, res.getStatus());
        }
    }

    @Test
    void testTaskDelete() {
        when(taskDB.deleteTask(eq("WRONG_APP"), anyString())).thenReturn(false);
        val spec = taskSpec();
        when(taskDB.deleteTask(spec.getSourceAppName(), spec.getTaskId())).thenReturn(true);
        {
            val res = re.taskDelete("WRONMG_APP", "T001");
            assertEquals(FAILED, res.getStatus());
        }
        {
            val res = re.taskDelete(spec.getSourceAppName(), spec.getTaskId());
            assertEquals(SUCCESS, res.getStatus());
        }
    }

    @Test
    void testLocalServices() {
        val rng = new SecureRandom();
        when(localServiceStateDB.services(0, Integer.MAX_VALUE))
                .thenReturn(IntStream.range(0, 100)
                                    .mapToObj(i -> {
                                        return createLocalService(i, rng.nextInt(100));
                                    })
                                    .toList());

        val res = re.localServices(0, Integer.MAX_VALUE);
        assertEquals(SUCCESS, res.getStatus());
        assertEquals("success", res.getMessage());
        assertEquals(100, res.getData().size());
    }

    @Test
    void testLocalService() {
        val spec = localServiceSpec();
        val serviceId = ControllerUtils.deployableObjectId(spec);

        val localServiceInfo = new LocalServiceInfo(serviceId, spec, 5, ActivationState.ACTIVE, new Date(), new Date());
        when(localServiceStateDB.service(serviceId))
                .thenReturn(Optional.of(localServiceInfo));

        val res = re.localService(serviceId);
        assertEquals(SUCCESS, res.getStatus());
        assertEquals("success", res.getMessage());
        assertNotNull(res.getData());
    }

    @Test
    void testLocalServiceSpec() {
        val spec = localServiceSpec();
        val serviceId = ControllerUtils.deployableObjectId(spec);

        val localServiceInfo = new LocalServiceInfo(serviceId, spec, 5, ActivationState.ACTIVE, new Date(), new Date());
        when(localServiceStateDB.service(serviceId))
                .thenReturn(Optional.of(localServiceInfo));

        val res = re.localServiceSpec(serviceId);
        assertEquals(SUCCESS, res.getStatus());
        assertEquals("success", res.getMessage());
        assertNotNull(res.getData());
        assertEquals(spec, res.getData());
    }

    @Test
    void testLocalServiceInstances() {
        val spec = localServiceSpec();
        val serviceId = ControllerUtils.deployableObjectId(spec);
        val instances = IntStream.rangeClosed(1, 100)
                .mapToObj(i -> generateInstanceInfo(serviceId, spec, i, LocalServiceInstanceState.STARTING))
                .toList();

        when(localServiceStateDB.instances(serviceId, LocalServiceInstanceState.ACTIVE_STATES, false))
                .thenReturn(instances);

        val res = re.localServiceInstances(serviceId, Set.of());
        assertEquals(SUCCESS, res.getStatus());
        assertEquals("success", res.getMessage());
        assertEquals(100, res.getData().size());
        assertEquals(instances, res.getData());
        assertTrue(re.localServiceInstances(serviceId, Set.of(LocalServiceInstanceState.HEALTHY)).getData().isEmpty());
        assertEquals(instances,
                     re.localServiceInstances(serviceId, Set.of(LocalServiceInstanceState.STARTING)).getData());
    }

    @Test
    void testLocalServiceInstanceDetails() {
        val spec = localServiceSpec();
        val serviceId = ControllerUtils.deployableObjectId(spec);
        val instance = generateInstanceInfo(serviceId, spec, 1, LocalServiceInstanceState.STARTING);

        final var instanceId = instance.getInstanceId();
        when(localServiceStateDB.instance(serviceId, instanceId)).thenReturn(Optional.of(instance));

        {
            val res = re.localServiceInstanceDetails(serviceId, instanceId);
            assertEquals(SUCCESS, res.getStatus());
            assertEquals("success", res.getMessage());
            assertEquals(instance, res.getData());
        }
        {
            val res = re.localServiceInstanceDetails(serviceId, "blah");
            assertEquals(FAILED, res.getStatus());
            assertEquals("No such local service instance " + serviceId + "/blah", res.getMessage());
            assertNull(res.getData());
        }
    }

    @Test
    void testLocalServiceOldInstances() {
        val spec = localServiceSpec();
        val serviceId = ControllerUtils.deployableObjectId(spec);
        val instances = IntStream.rangeClosed(1, 100)
                .mapToObj(i -> generateInstanceInfo(serviceId, spec, i, LocalServiceInstanceState.STOPPED))
                .toList();

        when(localServiceStateDB.oldInstances(serviceId))
                .thenReturn(instances);

        val res = re.localServiceOldInstances(serviceId);
        assertEquals(SUCCESS, res.getStatus());
        assertEquals("success", res.getMessage());
        assertEquals(100, res.getData().size());
        assertEquals(instances, res.getData());
    }

    @Test
    void testCluster() {
        val clusterSummary = new com.phonepe.drove.models.api.ClusterSummary(
                "leader:8080",
                ClusterState.NORMAL,
                10,
                100,
                90,
                5,
                20,
                18,
                50,
                100,
                150,
                102400,
                51200,
                153600,
                2,
                8,
                java.util.Map.of());

        val dashboardData = com.phonepe.drove.models.api.DashboardData.builder()
                .clusterSummary(clusterSummary)
                .appStats(com.phonepe.drove.models.api.DashboardData.AppStats.builder()
                        .appCountByState(Map.of(ApplicationState.RUNNING, 90L))
                        .topApps(List.of())
                        .totalHealthyInstances(500)
                        .build())
                .taskStats(com.phonepe.drove.models.api.DashboardData.TaskStats.builder()
                        .taskCountByState(Map.of())
                        .topTasks(List.of())
                        .build())
                .serviceStats(com.phonepe.drove.models.api.DashboardData.ServiceStats.builder()
                        .serviceCountByState(Map.of())
                        .serviceCountByActivationState(Map.of())
                        .topServices(List.of())
                        .totalHealthyInstances(0)
                        .build())
                .executorStats(com.phonepe.drove.models.api.DashboardData.ExecutorStats.builder()
                        .executorCountByState(Map.of())
                        .utilization(com.phonepe.drove.models.api.DashboardData.UtilizationStats.builder()
                                .averageUtilization(60.0)
                                .highestUtilization(90.0)
                                .lowestUtilization(30.0)
                                .balanceScore(0.8)
                                .build())
                        .build())
                .generatedAt(new Date())
                .build();

        when(dashboardDataSource.current()).thenReturn(Optional.of(dashboardData));

        val r = re.cluster();
        assertEquals(SUCCESS, r.getStatus());
        assertEquals("success", r.getMessage());
        val c = r.getData();
        assertNotNull(c);
        assertEquals(clusterSummary, c);
        assertEquals(ClusterState.NORMAL, c.getState());
        assertEquals(10, c.getNumExecutors());
        assertEquals(100, c.getNumApplications());
        assertEquals(90, c.getNumActiveApplications());
        assertEquals(5, c.getNumActiveTasks());
        assertEquals(20, c.getNumLocalServices());
        assertEquals(18, c.getNumActiveLocalServices());
    }

    @Test
    void testClusterNotAvailable() {
        when(dashboardDataSource.current()).thenReturn(Optional.empty());

        val r = re.cluster();
        assertEquals(FAILED, r.getStatus());
        assertEquals("Could not fetch cluster summary", r.getMessage());
        assertNull(r.getData());
    }

    @Test
    void testNodes() {
        when(clusterResourcesDB.currentSnapshot(false))
                .thenReturn(IntStream.rangeClosed(1, 10)
                                    .mapToObj(ControllerTestUtils::executorHost)
                                    .toList());
        val r = re.nodes();
        assertEquals(SUCCESS, r.getStatus());
        val c = r.getData();
        assertNotNull(c);
        val l = r.getData();
        assertEquals(10, l.size());
    }

    @Test
    void testExecutorDetails() {
        val instanceData = executorHost(8080);

        val executorId = instanceData.getExecutorId();
        when(clusterResourcesDB.currentSnapshot(executorId)).thenReturn(Optional.of(instanceData));

        {
            val r = re.executorDetails(executorId);
            assertEquals(SUCCESS, r.getStatus());
            assertEquals(instanceData.getNodeData(), r.getData());
        }
        {
            val r = re.executorDetails("invalid");
            assertEquals(FAILED, r.getStatus());
            assertNull(r.getData());
            assertEquals("No executor found with id: invalid", r.getMessage());
        }
    }

    @Test
    void testEndpoints() {

        val apps = IntStream.rangeClosed(1, 100)
                .mapToObj(i -> createApp(i, 10))
                .toList();
        when(applicationStateDB.applications(0, Integer.MAX_VALUE)).thenReturn(apps);
        when(appEngine.currentState(anyString())).thenReturn(Optional.of(ApplicationState.RUNNING));
        val instances = apps.stream()
                .flatMap(applicationInfo -> LongStream.rangeClosed(1, applicationInfo.getInstances())
                        .mapToObj(i -> generateInstanceInfo(applicationInfo.getAppId(),
                                                            applicationInfo.getSpec(),
                                                            (int) i))
                        .toList()
                        .stream())
                .collect(Collectors.groupingBy(InstanceInfo::getAppId));

        when(instanceInfoDB.instances(anyList(), anySet(), eq(false))).thenReturn(instances);


        val r = re.endpoints(Set.of());
        assertEquals(SUCCESS, r.getStatus());
        assertEquals(100, r.getData().size());
        r.getData().forEach(exposedAppInfo -> assertEquals(10, exposedAppInfo.getHosts().size()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testEndpointsForApp() {
        val apps = IntStream.rangeClosed(1, 100)
                .mapToObj(i -> createApp(i, 10))
                .toList();
        when(applicationStateDB.applications(0, Integer.MAX_VALUE)).thenReturn(apps);
        when(appEngine.currentState(anyString())).thenReturn(Optional.of(ApplicationState.RUNNING));
        val instances = apps.stream()
                .flatMap(applicationInfo -> LongStream.rangeClosed(1, applicationInfo.getInstances())
                        .mapToObj(i -> generateInstanceInfo(applicationInfo.getAppId(),
                                                            applicationInfo.getSpec(),
                                                            (int) i))
                        .toList()
                        .stream())
                .collect(Collectors.groupingBy(InstanceInfo::getAppId));


        when(instanceInfoDB.instances(anyList(), anySet(), eq(false)))
                .thenAnswer(invocationOnMock -> {
                    val appIds = (List<String>) invocationOnMock.getArgument(0);
                    return Map.copyOf(Maps.filterKeys(instances, appIds::contains));
                });

        apps.stream()
                .map(app -> app.getSpec().getName())
                .forEach(appName -> {
                    val r = re.endpoints(Set.of(appName));
                    assertEquals(SUCCESS, r.getStatus());
                    assertEquals(1, r.getData().size());
                    r.getData().forEach(exposedAppInfo -> assertEquals(10, exposedAppInfo.getHosts().size()));
                });

    }

    @Test
    void testEndpointsMultipleExposureSpecs() {
        val appSpec = appSpec("TEST_APP_MULTI", 1)
                .withExposureSpec(List.of(new ExposureSpec("vhost1.appform.io", "main", ExposureMode.ALL),
                                          new ExposureSpec("vhost2.appform.io", "main", ExposureMode.ALL),
                                          new ExposureSpec("vhost3.appform.io", "main", ExposureMode.ALL)));
        val appId = ControllerUtils.deployableObjectId(appSpec);
        val app = new ApplicationInfo(appId, appSpec, 10, new Date(), new Date());
        when(applicationStateDB.applications(0, Integer.MAX_VALUE)).thenReturn(List.of(app));
        when(appEngine.currentState(anyString())).thenReturn(Optional.of(ApplicationState.RUNNING));
        val instances = LongStream.rangeClosed(1, app.getInstances())
                .mapToObj(i -> generateInstanceInfo(app.getAppId(), app.getSpec(), (int) i))
                .toList();
        when(instanceInfoDB.instances(anyList(), anySet(), eq(false)))
                .thenReturn(Map.of(appId, instances));

        val r = re.endpoints(Set.of());
        assertEquals(SUCCESS, r.getStatus());
        assertEquals(3, r.getData().size());
        assertEquals(Set.of("vhost1.appform.io", "vhost2.appform.io", "vhost3.appform.io"),
                     r.getData().stream().map(ExposedAppInfo::getVhost).collect(Collectors.toSet()));
        r.getData().forEach(exposedAppInfo -> assertEquals(10, exposedAppInfo.getHosts().size()));
    }

    @Test
    void testBlacklistExecutors() {
        testBlacklistingMultiFunctionality(ResponseEngine::blacklistExecutors);
    }

    @Test
    void testUnblacklistExecutors() {
        testBlacklistingMultiFunctionality(ResponseEngine::unblacklistExecutors);
    }

    @Test
    void testSetClusterMaintenanceMode() {
        testMaintenanceFunctionality(ClusterState.MAINTENANCE, ResponseEngine::setClusterMaintenanceMode);
    }

    @Test
    void testUnsetClusterMaintenanceMode() {
        testMaintenanceFunctionality(ClusterState.NORMAL, ResponseEngine::unsetClusterMaintenanceMode);
    }


    @Test
    void testEvents() {
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.onLeadershipStateChanged()).thenReturn(new ConsumingSyncSignal<>());
        val eventStore = new InMemoryEventStore(leadershipEnsurer, ControllerOptions.DEFAULT,
                                                new ClusterMetricsRegistry(SharedMetricRegistries.getOrCreate("test")));
        val event = new DroveClusterMaintenanceModeSetEvent(EventUtils.controllerMetadata());
        eventStore.recordEvent(event);
        val re = new ResponseEngine(leadershipObserver,
                                    appEngine,
                                    applicationStateDB,
                                    instanceInfoDB,
                                    taskDB,
                                    taskEngine,
                                    lsEngine,
                                    localServiceStateDB,
                                    clusterStateDB,
                                    clusterResourcesDB,
                                    eventStore,
                                    communicator,
                                    eventBus,
                                    blacklistingManager,
                                    dashboardDataSource);

        val r = re.events(0, 10);
        assertEquals(SUCCESS, r.getStatus());
        assertEquals(1, r.getData().size());

        assertEquals(event, r.getData().get(0));
    }

    @SuppressWarnings("unchecked")
    private<T> void testBlacklistingMultiFunctionality(
            final BiFunction<ResponseEngine, Set<String>, ApiResponse<BlacklistOperationResponse>> func) {
        val executors = IntStream.rangeClosed(1, 10)
            .mapToObj(i -> executorHost(i, 8080, List.of(), List.of(), List.of()))
            .collect(Collectors.toMap(ExecutorHostInfo::getExecutorId, Function.identity()));
        when(clusterResourcesDB.currentSnapshot(false)).thenReturn(List.copyOf(executors.values()));
        when(clusterResourcesDB.currentSnapshot(anyString()))
            .thenAnswer(invocationOnMock -> {
                val eId = invocationOnMock.getArgument(0, String.class);
                return Optional.ofNullable(executors.get(eId));
            });
        val success = new AtomicBoolean(true);
        when(blacklistingManager.blacklistExecutors(anySet()))
            .thenAnswer((Answer<BlacklistOperationResponse>) invocationOnMock -> {
                val response = invocationOnMock.getArgument(0);
                return success.get()
                    ? BlacklistOperationResponse.builder()
                    .successful(Set.copyOf((Set<String>)response))
                    .failed(Set.of())
                    .approxCompletionTimeMs(1000)
                    .build()
                    : BlacklistOperationResponse.builder()
                    .successful(Set.of())
                    .failed(Set.copyOf((Set<String>)response))
                    .build();
            });
        when(blacklistingManager.unblacklistExecutors(anySet()))
            .thenAnswer((Answer<BlacklistOperationResponse>) invocationOnMock -> {
                val response = invocationOnMock.getArgument(0);
                return success.get()
                    ? BlacklistOperationResponse.builder()
                    .successful(Set.copyOf((Set<String>)response))
                    .failed(Set.of())
                    .approxCompletionTimeMs(1000)
                    .build()
                    : BlacklistOperationResponse.builder()
                    .successful(Set.of())
                    .failed(Set.copyOf((Set<String>)response))
                    .build();
            });
        val executorIds = executors.keySet();
        {
            val r = func.apply(re, executorIds);
            assertEquals(SUCCESS, r.getStatus());
            assertEquals(executorIds, r.getData().getSuccessful());
        }
        {
            success.set(false);
            val r = func.apply(re, executorIds);
            assertEquals(SUCCESS, r.getStatus());
            assertEquals(executorIds, r.getData().getFailed());
        }
    }

    private void testMaintenanceFunctionality(
            final ClusterState state,
            final Function<ResponseEngine, ApiResponse<ClusterStateData>> func) {

        val success = new AtomicBoolean(true);
        when(clusterStateDB.setClusterState(state))
                .thenAnswer((Answer<Optional<ClusterStateData>>) invocationOnMock ->
                        success.get()
                        ? Optional.of(new ClusterStateData(state, new Date()))
                        : Optional.empty());
        {
            val r = func.apply(re);
            assertEquals(SUCCESS, r.getStatus());
        }
        {
            success.set(false);
            val r = func.apply(re);
            assertEquals(FAILED, r.getStatus());
            assertEquals("Could not change cluster state", r.getMessage());
        }
    }

    private ApplicationInfo createApp(int i, int instances) {
        val appSpec = appSpec("TEST_APP_" + i, 1);
        val appId = ControllerUtils.deployableObjectId(appSpec);
        return new ApplicationInfo(appId, appSpec, instances, new Date(), new Date());
    }

    private LocalServiceInfo createLocalService(int i, int instances) {
        val serviceSpec = localServiceSpec("TEST_SERVICE_" + i, 1);
        val serviceId = ControllerUtils.deployableObjectId(serviceSpec);
        return new LocalServiceInfo(serviceId, serviceSpec, instances, ActivationState.ACTIVE, new Date(), new Date());
    }



    @Test
    void testDashboardData() {
        val dashboardData = DashboardData.builder()
                .clusterSummary(new ClusterSummary(
                        "leader:8080",
                        ClusterState.NORMAL,
                        10,
                        100,
                        90,
                        5,
                        20,
                        18,
                        50,
                        100,
                        150,
                        102400,
                        51200,
                        153600,
                        2,
                        8,
                        java.util.Map.of()))
                .appStats(DashboardData.AppStats.builder()
                        .appCountByState(Map.of(ApplicationState.RUNNING, 90L))
                        .topApps(List.of())
                        .totalHealthyInstances(500)
                        .build())
                .taskStats(DashboardData.TaskStats.builder()
                        .taskCountByState(Map.of())
                        .topTasks(List.of())
                        .build())
                .serviceStats(DashboardData.ServiceStats.builder()
                        .serviceCountByState(Map.of())
                        .serviceCountByActivationState(Map.of())
                        .topServices(List.of())
                        .totalHealthyInstances(0)
                        .build())
                .executorStats(DashboardData.ExecutorStats.builder()
                        .executorCountByState(Map.of())
                        .utilization(DashboardData.UtilizationStats.builder()
                                .averageUtilization(60.0)
                                .highestUtilization(90.0)
                                .lowestUtilization(30.0)
                                .balanceScore(0.8)
                                .build())
                        .build())
                .generatedAt(new Date())
                .build();

        when(dashboardDataSource.current()).thenReturn(Optional.of(dashboardData));

        val res = re.dashboardData();
        assertEquals(SUCCESS, res.getStatus());
        assertEquals("success", res.getMessage());
        assertEquals(dashboardData, res.getData());
    }

    @Test
    void testDashboardDataNotAvailable() {
        when(dashboardDataSource.current()).thenReturn(Optional.empty());

        val res = re.dashboardData();
        assertEquals(FAILED, res.getStatus());
        assertEquals("Could not fetch dashboard data", res.getMessage());
        assertNull(res.getData());
    }

    @Test
    void testGpusEmpty() {
        when(clusterResourcesDB.currentSnapshot(false))
                .thenReturn(IntStream.rangeClosed(1, 5)
                                    .mapToObj(ControllerTestUtils::executorHost)
                                    .toList());
        val r = re.gpus();
        assertEquals(SUCCESS, r.getStatus());
        assertNotNull(r.getData());
        assertTrue(r.getData().isEmpty());
    }

    @Test
    void testGpusWithDevices() {
        val gpuId1 = "GPU-aaaa-1111";
        val gpuId2 = "GPU-bbbb-2222";
        val gpuInfo1 = new com.phonepe.drove.models.info.GPUInfo(
                gpuId1,
                com.phonepe.drove.models.application.requirements.GpuVendor.NVIDIA,
                "A100-SXM4-40GB",
                0);
        val gpuInfo2 = new com.phonepe.drove.models.info.GPUInfo(
                gpuId2,
                com.phonepe.drove.models.application.requirements.GpuVendor.NVIDIA,
                "A100-SXM4-40GB",
                1);
        val layout = com.phonepe.drove.models.info.resources.PhysicalLayout.builder()
                .cores(Map.of(0, Set.of(0, 1, 2, 3, 4)))
                .memory(Map.of(0, 4L * 128 * (2L ^ 20)))
                .gpus(Map.of(gpuId1, gpuInfo1, gpuId2, gpuInfo2))
                .build();
        val snapshot = com.phonepe.drove.models.info.ExecutorResourceSnapshot.builder()
                .executorId("Ex1")
                .cpus(new com.phonepe.drove.models.info.resources.available.AvailableCPU(
                        Map.of(0, Set.of(2, 3, 4)),
                        Map.of(1, Set.of(0, 1))))
                .memory(new com.phonepe.drove.models.info.resources.available.AvailableMemory(
                        Map.of(0, 3 * 128 * (2L ^ 20)),
                        Map.of(0, 128 * (2L ^ 20))))
                .gpus(new com.phonepe.drove.models.info.resources.available.AvailableGPU(
                        List.of(gpuId1),
                        List.of(gpuId2)))
                .layout(layout)
                .build();
        val executorData = new com.phonepe.drove.models.info.nodedata.ExecutorNodeData(
                "host1",
                8080,
                com.phonepe.drove.models.info.nodedata.NodeTransportType.HTTP,
                new Date(),
                snapshot,
                List.of(),
                List.of(),
                List.of(),
                Set.of(),
                Map.of(),
                com.phonepe.drove.models.info.nodedata.ExecutorState.ACTIVE);
        val hostInfo = new ExecutorHostInfo(
                "Ex1",
                executorData,
                Map.of(0, new ExecutorHostInfo.NumaNodeInfo()),
                new ExecutorHostInfo.HostGPUInfo());

        when(clusterResourcesDB.currentSnapshot(false)).thenReturn(List.of(hostInfo));

        val r = re.gpus();
        assertEquals(SUCCESS, r.getStatus());
        assertNotNull(r.getData());
        assertEquals(2, r.getData().size());

        val freeGpu = r.getData().stream()
                .filter(g -> g.getGpuId().equals(gpuId1))
                .findFirst()
                .orElseThrow();
        assertEquals("Ex1", freeGpu.getExecutorId());
        assertEquals("host1", freeGpu.getHostname());
        assertEquals(com.phonepe.drove.models.application.requirements.GpuVendor.NVIDIA, freeGpu.getVendor());
        assertEquals("A100-SXM4-40GB", freeGpu.getModel());
        assertEquals(GpuResourceInfo.GpuState.FREE, freeGpu.getState());
        assertEquals(0, freeGpu.getNumaNodeAffinity());

        val usedGpu = r.getData().stream()
                .filter(g -> g.getGpuId().equals(gpuId2))
                .findFirst()
                .orElseThrow();
        assertEquals(GpuResourceInfo.GpuState.IN_USE, usedGpu.getState());
        assertEquals(1, usedGpu.getNumaNodeAffinity());
    }


}
