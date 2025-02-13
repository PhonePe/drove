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

import com.codahale.metrics.SharedMetricRegistries;
import com.google.common.collect.Maps;
import com.phonepe.drove.common.discovery.leadership.LeadershipObserver;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.executor.ExecutorMessage;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.engine.ApplicationLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.LocalServiceLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.TaskEngine;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.event.EventStore;
import com.phonepe.drove.controller.event.InMemoryEventStore;
import com.phonepe.drove.controller.managed.BlacklistingAppMovementManager;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.controller.metrics.ClusterMetricsRegistry;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.statedb.*;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.controller.utils.EventUtils;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.common.ClusterState;
import com.phonepe.drove.models.common.ClusterStateData;
import com.phonepe.drove.models.events.events.DroveClusterMaintenanceModeSetEvent;
import com.phonepe.drove.models.info.nodedata.ControllerNodeData;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.localservice.ActivationState;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static com.phonepe.drove.controller.ControllerTestUtils.*;
import static com.phonepe.drove.models.api.ApiErrorCode.FAILED;
import static com.phonepe.drove.models.api.ApiErrorCode.SUCCESS;
import static com.phonepe.drove.models.info.nodedata.NodeTransportType.HTTP;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 *
 */
class ResponseEngineTest {

    private final LeadershipObserver leadershipObserver = mock(LeadershipObserver.class);
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
    private final BlacklistingAppMovementManager blacklistingAppMovementManager =
            mock(BlacklistingAppMovementManager.class);
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
                                                         blacklistingAppMovementManager);

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
              blacklistingAppMovementManager);
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
        when(leadershipObserver.leader()).thenReturn(Optional.of(new ControllerNodeData("test-controller",
                                                                                        8080,
                                                                                        HTTP,
                                                                                        new Date(),
                                                                                        true)));
        when(clusterResourcesDB.currentSnapshot(true))
                .thenReturn(IntStream.rangeClosed(1, 10)
                                    .mapToObj(ControllerTestUtils::executorHost)
                                    .toList());
        when(applicationStateDB.applications(0, Integer.MAX_VALUE))
                .thenReturn(IntStream.rangeClosed(1, 100)
                                    .mapToObj(i -> createApp(i, 10))
                                    .toList());
        when(appEngine.currentState(anyString())).thenAnswer(new Answer<Optional<ApplicationState>>() {
            int count = 0;

            @Override
            public Optional<ApplicationState> answer(InvocationOnMock invocationOnMock) {
                return (++count) > 50 ? Optional.of(ApplicationState.RUNNING) : Optional.empty();
            }
        });

        {
            when(clusterStateDB.currentState()).thenReturn(Optional.of(new ClusterStateData(ClusterState.MAINTENANCE,
                                                                                            new Date())));
            val r = re.cluster();
            assertEquals(SUCCESS, r.getStatus());
            val c = r.getData();
            assertNotNull(c);
            assertEquals(ClusterState.MAINTENANCE, c.getState());
            assertEquals(10, c.getNumExecutors());
            assertEquals(100, c.getNumApplications());
            assertEquals(50, c.getNumActiveApplications());
            assertEquals(30, c.getFreeCores());
            assertEquals(20, c.getUsedCores());
            assertEquals(50, c.getTotalCores());
            assertEquals(84480, c.getFreeMemory());
            assertEquals(28160, c.getUsedMemory());
            assertEquals(112640, c.getTotalMemory());
            reset(clusterStateDB);
        }
        {
            when(clusterStateDB.currentState()).thenReturn(Optional.empty());
            val r = re.cluster();
            assertEquals(SUCCESS, r.getStatus());
            val c = r.getData();
            assertNotNull(c);
            assertEquals(ClusterState.NORMAL, c.getState());
            assertEquals(10, c.getNumExecutors());
            assertEquals(100, c.getNumApplications());
            assertEquals(100, c.getNumActiveApplications());
            assertEquals(30, c.getFreeCores());
            assertEquals(20, c.getUsedCores());
            assertEquals(50, c.getTotalCores());
            assertEquals(84480, c.getFreeMemory());
            assertEquals(28160, c.getUsedMemory());
            assertEquals(112640, c.getTotalMemory());
            reset(clusterStateDB);
        }
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
                                    blacklistingAppMovementManager);

        val r = re.events(0, 10);
        assertEquals(SUCCESS, r.getStatus());
        assertEquals(1, r.getData().size());

        assertEquals(event, r.getData().get(0));
    }

    private void testBlacklistingMultiFunctionality(
            final BiFunction<ResponseEngine, Set<String>, ApiResponse<Map<String, Set<String>>>> func) {
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
        when(communicator.send(any(ExecutorMessage.class))).thenAnswer((Answer<MessageResponse>) invocationOnMock -> {
            val header = invocationOnMock.getArgument(0, ExecutorMessage.class).getHeader();
            return success.get()
                   ? new MessageResponse(header, MessageDeliveryStatus.ACCEPTED)
                   : new MessageResponse(header, MessageDeliveryStatus.FAILED);
        });
        val executorIds = executors.keySet();
        {
            val r = func.apply(re, executorIds);
            assertEquals(SUCCESS, r.getStatus());
            assertEquals(executorIds, r.getData().get("successful"));
        }
        {
            success.set(false);
            val r = func.apply(re, executorIds);
            assertEquals(SUCCESS, r.getStatus());
            assertEquals(executorIds, r.getData().get("failed"));
        }
    }

    private void testBlacklistingFunctionality(
            final BiFunction<ResponseEngine, String, ApiResponse<Map<String,
                    String>>> func) {
        val executor = executorHost(8080);
        when(clusterResourcesDB.currentSnapshot(executor.getExecutorId())).thenReturn(Optional.of(executor));

        val success = new AtomicBoolean(true);
        when(communicator.send(any(ExecutorMessage.class))).thenAnswer((Answer<MessageResponse>) invocationOnMock -> {
            val header = invocationOnMock.getArgument(0, ExecutorMessage.class).getHeader();
            return success.get()
                   ? new MessageResponse(header, MessageDeliveryStatus.ACCEPTED)
                   : new MessageResponse(header, MessageDeliveryStatus.FAILED);
        });
        {
            val r = func.apply(re, executor.getExecutorId());
            assertEquals(SUCCESS, r.getStatus());
        }
        {
            success.set(false);
            val r = func.apply(re, executor.getExecutorId());
            assertEquals(FAILED, r.getStatus());
//            assertEquals("Error sending remote message", r.getMessage());
        }
        {
            val r = func.apply(re, "invalid-exec");
            assertEquals(FAILED, r.getStatus());
//            assertEquals("Failed to blacklist executor. Check logs for error details", r.getMessage());
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


}