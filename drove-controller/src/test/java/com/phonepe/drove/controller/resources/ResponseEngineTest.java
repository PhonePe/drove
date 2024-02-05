package com.phonepe.drove.controller.resources;

import com.phonepe.drove.common.discovery.leadership.LeadershipObserver;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.executor.ExecutorMessage;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.engine.ApplicationEngine;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.event.EventStore;
import com.phonepe.drove.controller.event.InMemoryEventStore;
import com.phonepe.drove.controller.managed.BlacklistingAppMovementManager;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.ClusterStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
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
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.val;
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

    @Test
    void testApplications() {
        val leadershipObserver = mock(LeadershipObserver.class);
        val engine = mock(ApplicationEngine.class);
        val applicationStateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val taskDB = mock(TaskDB.class);
        val eventStore = mock(EventStore.class);
        val communicator = mock(ControllerCommunicator.class);
        val eventBus = mock(DroveEventBus.class);
        val blacklistingAppMovementManager = mock(BlacklistingAppMovementManager.class);

        val re = new ResponseEngine(leadershipObserver,
                                    engine,
                                    applicationStateDB,
                                    instanceInfoDB,
                                    taskDB, clusterStateDB,
                                    clusterResourcesDB,
                                    eventStore,
                                    communicator,
                                    eventBus,
                                    blacklistingAppMovementManager);
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
        val leadershipObserver = mock(LeadershipObserver.class);
        val engine = mock(ApplicationEngine.class);
        val applicationStateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val taskDB = mock(TaskDB.class);
        val eventStore = mock(EventStore.class);
        val communicator = mock(ControllerCommunicator.class);
        val eventBus = mock(DroveEventBus.class);
        val blacklistingAppMovementManager = mock(BlacklistingAppMovementManager.class);
        val re = new ResponseEngine(leadershipObserver,
                                    engine,
                                    applicationStateDB,
                                    instanceInfoDB,
                                    taskDB, clusterStateDB,
                                    clusterResourcesDB,
                                    eventStore,
                                    communicator,
                                    eventBus, blacklistingAppMovementManager);

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
        val leadershipObserver = mock(LeadershipObserver.class);
        val engine = mock(ApplicationEngine.class);
        val applicationStateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val taskDB = mock(TaskDB.class);
        val eventStore = mock(EventStore.class);
        val communicator = mock(ControllerCommunicator.class);
        val eventBus = mock(DroveEventBus.class);
        val blacklistingAppMovementManager = mock(BlacklistingAppMovementManager.class);
        val re = new ResponseEngine(leadershipObserver,
                                    engine,
                                    applicationStateDB,
                                    instanceInfoDB,
                                    taskDB, clusterStateDB,
                                    clusterResourcesDB,
                                    eventStore,
                                    communicator,
                                    eventBus, blacklistingAppMovementManager);

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
        val leadershipObserver = mock(LeadershipObserver.class);
        val engine = mock(ApplicationEngine.class);
        val applicationStateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val taskDB = mock(TaskDB.class);
        val eventStore = mock(EventStore.class);
        val communicator = mock(ControllerCommunicator.class);
        val eventBus = mock(DroveEventBus.class);
        val blacklistingAppMovementManager = mock(BlacklistingAppMovementManager.class);
        val re = new ResponseEngine(leadershipObserver,
                                    engine,
                                    applicationStateDB,
                                    instanceInfoDB,
                                    taskDB, clusterStateDB,
                                    clusterResourcesDB,
                                    eventStore,
                                    communicator,
                                    eventBus, blacklistingAppMovementManager);
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
        val leadershipObserver = mock(LeadershipObserver.class);
        val engine = mock(ApplicationEngine.class);
        val applicationStateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val taskDB = mock(TaskDB.class);
        val eventStore = mock(EventStore.class);
        val communicator = mock(ControllerCommunicator.class);
        val eventBus = mock(DroveEventBus.class);
        val blacklistingAppMovementManager = mock(BlacklistingAppMovementManager.class);
        val re = new ResponseEngine(leadershipObserver,
                                    engine,
                                    applicationStateDB,
                                    instanceInfoDB,
                                    taskDB, clusterStateDB,
                                    clusterResourcesDB,
                                    eventStore,
                                    communicator,
                                    eventBus, blacklistingAppMovementManager);
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
        val leadershipObserver = mock(LeadershipObserver.class);
        val engine = mock(ApplicationEngine.class);
        val applicationStateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val taskDB = mock(TaskDB.class);
        val eventStore = mock(EventStore.class);
        val communicator = mock(ControllerCommunicator.class);
        val eventBus = mock(DroveEventBus.class);
        val blacklistingAppMovementManager = mock(BlacklistingAppMovementManager.class);
        val re = new ResponseEngine(leadershipObserver,
                                    engine,
                                    applicationStateDB,
                                    instanceInfoDB,
                                    taskDB, clusterStateDB,
                                    clusterResourcesDB,
                                    eventStore,
                                    communicator,
                                    eventBus, blacklistingAppMovementManager);
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
        val leadershipObserver = mock(LeadershipObserver.class);
        val engine = mock(ApplicationEngine.class);
        val applicationStateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val taskDB = mock(TaskDB.class);
        val eventStore = mock(EventStore.class);
        val communicator = mock(ControllerCommunicator.class);
        val eventBus = mock(DroveEventBus.class);
        val blacklistingAppMovementManager = mock(BlacklistingAppMovementManager.class);
        val re = new ResponseEngine(leadershipObserver,
                                    engine,
                                    applicationStateDB,
                                    instanceInfoDB,
                                    taskDB, clusterStateDB,
                                    clusterResourcesDB,
                                    eventStore,
                                    communicator,
                                    eventBus, blacklistingAppMovementManager);
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
        val leadershipObserver = mock(LeadershipObserver.class);
        val engine = mock(ApplicationEngine.class);
        val applicationStateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val taskDB = mock(TaskDB.class);
        val eventStore = mock(EventStore.class);
        val communicator = mock(ControllerCommunicator.class);
        val eventBus = mock(DroveEventBus.class);
        val blacklistingAppMovementManager = mock(BlacklistingAppMovementManager.class);
        val re = new ResponseEngine(leadershipObserver,
                                    engine,
                                    applicationStateDB,
                                    instanceInfoDB,
                                    taskDB, clusterStateDB,
                                    clusterResourcesDB,
                                    eventStore,
                                    communicator,
                                    eventBus, blacklistingAppMovementManager);
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
    void testCluster() {
        val leadershipObserver = mock(LeadershipObserver.class);
        val engine = mock(ApplicationEngine.class);
        val applicationStateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val taskDB = mock(TaskDB.class);
        val eventStore = mock(EventStore.class);
        val communicator = mock(ControllerCommunicator.class);
        val eventBus = mock(DroveEventBus.class);
        val blacklistingAppMovementManager = mock(BlacklistingAppMovementManager.class);
        val re = new ResponseEngine(leadershipObserver,
                                    engine,
                                    applicationStateDB,
                                    instanceInfoDB,
                                    taskDB, clusterStateDB,
                                    clusterResourcesDB,
                                    eventStore,
                                    communicator,
                                    eventBus, blacklistingAppMovementManager);
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
        when(engine.applicationState(anyString())).thenAnswer(new Answer<Optional<ApplicationState>>() {
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
        val leadershipObserver = mock(LeadershipObserver.class);
        val engine = mock(ApplicationEngine.class);
        val applicationStateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val taskDB = mock(TaskDB.class);
        val eventStore = mock(EventStore.class);
        val communicator = mock(ControllerCommunicator.class);
        val eventBus = mock(DroveEventBus.class);
        val blacklistingAppMovementManager = mock(BlacklistingAppMovementManager.class);
        val re = new ResponseEngine(leadershipObserver,
                                    engine,
                                    applicationStateDB,
                                    instanceInfoDB,
                                    taskDB, clusterStateDB,
                                    clusterResourcesDB,
                                    eventStore,
                                    communicator,
                                    eventBus, blacklistingAppMovementManager);
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
        val leadershipObserver = mock(LeadershipObserver.class);
        val engine = mock(ApplicationEngine.class);
        val applicationStateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val taskDB = mock(TaskDB.class);
        val eventStore = mock(EventStore.class);
        val communicator = mock(ControllerCommunicator.class);
        val eventBus = mock(DroveEventBus.class);
        val blacklistingAppMovementManager = mock(BlacklistingAppMovementManager.class);
        val re = new ResponseEngine(leadershipObserver,
                                    engine,
                                    applicationStateDB,
                                    instanceInfoDB,
                                    taskDB, clusterStateDB,
                                    clusterResourcesDB,
                                    eventStore,
                                    communicator,
                                    eventBus, blacklistingAppMovementManager);

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
        val leadershipObserver = mock(LeadershipObserver.class);
        val engine = mock(ApplicationEngine.class);
        val applicationStateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val taskDB = mock(TaskDB.class);
        val eventStore = mock(EventStore.class);
        val communicator = mock(ControllerCommunicator.class);
        val eventBus = mock(DroveEventBus.class);
        val blacklistingAppMovementManager = mock(BlacklistingAppMovementManager.class);
        val re = new ResponseEngine(leadershipObserver,
                                    engine,
                                    applicationStateDB,
                                    instanceInfoDB,
                                    taskDB, clusterStateDB,
                                    clusterResourcesDB,
                                    eventStore,
                                    communicator,
                                    eventBus, blacklistingAppMovementManager);

        val apps = IntStream.rangeClosed(1, 100)
                .mapToObj(i -> createApp(i, 10))
                .toList();
        when(applicationStateDB.applications(0, Integer.MAX_VALUE)).thenReturn(apps);
        when(engine.applicationState(anyString())).thenReturn(Optional.of(ApplicationState.RUNNING));
        val instances = apps.stream()
                .flatMap(applicationInfo -> LongStream.rangeClosed(1, applicationInfo.getInstances())
                        .mapToObj(i -> generateInstanceInfo(applicationInfo.getAppId(),
                                                            applicationInfo.getSpec(),
                                                            (int) i))
                        .toList()
                        .stream())
                .collect(Collectors.groupingBy(InstanceInfo::getAppId));

        when(instanceInfoDB.instances(anyList(), anySet(), eq(false))).thenReturn(instances);


        val r = re.endpoints();
        assertEquals(SUCCESS, r.getStatus());
        assertEquals(100, r.getData().size());
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
        val leadershipObserver = mock(LeadershipObserver.class);
        val engine = mock(ApplicationEngine.class);
        val applicationStateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val taskDB = mock(TaskDB.class);
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.onLeadershipStateChanged()).thenReturn(new ConsumingSyncSignal<>());
        val eventStore = new InMemoryEventStore(leadershipEnsurer, ControllerOptions.DEFAULT);
        val communicator = mock(ControllerCommunicator.class);
        val eventBus = mock(DroveEventBus.class);
        val blacklistingAppMovementManager = mock(BlacklistingAppMovementManager.class);

        val event = new DroveClusterMaintenanceModeSetEvent(EventUtils.controllerMetadata());
        eventStore.recordEvent(event);
        val re = new ResponseEngine(leadershipObserver,
                                    engine,
                                    applicationStateDB,
                                    instanceInfoDB,
                                    taskDB, clusterStateDB,
                                    clusterResourcesDB,
                                    eventStore,
                                    communicator,
                                    eventBus, blacklistingAppMovementManager);

        val r = re.events(0, 10);
        assertEquals(SUCCESS, r.getStatus());
        assertEquals(1, r.getData().size());

        assertEquals(event, r.getData().get(0));
    }

    private void testBlacklistingMultiFunctionality(
            final BiFunction<ResponseEngine, Set<String>, ApiResponse<Map<String, Set<String>>>> func) {
        val leadershipObserver = mock(LeadershipObserver.class);
        val engine = mock(ApplicationEngine.class);
        val applicationStateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val taskDB = mock(TaskDB.class);
        val eventStore = mock(EventStore.class);
        val communicator = mock(ControllerCommunicator.class);
        val eventBus = mock(DroveEventBus.class);
        val blacklistingAppMovementManager = mock(BlacklistingAppMovementManager.class);
        val re = new ResponseEngine(leadershipObserver,
                                    engine,
                                    applicationStateDB,
                                    instanceInfoDB,
                                    taskDB, clusterStateDB,
                                    clusterResourcesDB,
                                    eventStore,
                                    communicator,
                                    eventBus, blacklistingAppMovementManager);
        val executors = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> executorHost(i, 8080, List.of(), List.of()))
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

    private void testBlacklistingFunctionality(final BiFunction<ResponseEngine, String, ApiResponse<Map<String, String>>> func) {
        val leadershipObserver = mock(LeadershipObserver.class);
        val engine = mock(ApplicationEngine.class);
        val applicationStateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val taskDB = mock(TaskDB.class);
        val eventStore = mock(EventStore.class);
        val communicator = mock(ControllerCommunicator.class);
        val eventBus = mock(DroveEventBus.class);
        val blacklistingAppMovementManager = mock(BlacklistingAppMovementManager.class);
        val re = new ResponseEngine(leadershipObserver,
                                    engine,
                                    applicationStateDB,
                                    instanceInfoDB,
                                    taskDB, clusterStateDB,
                                    clusterResourcesDB,
                                    eventStore,
                                    communicator,
                                    eventBus, blacklistingAppMovementManager);
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
        val leadershipObserver = mock(LeadershipObserver.class);
        val engine = mock(ApplicationEngine.class);
        val applicationStateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val taskDB = mock(TaskDB.class);
        val eventStore = mock(EventStore.class);
        val communicator = mock(ControllerCommunicator.class);
        val eventBus = mock(DroveEventBus.class);
        val blacklistingAppMovementManager = mock(BlacklistingAppMovementManager.class);
        val re = new ResponseEngine(leadershipObserver,
                                    engine,
                                    applicationStateDB,
                                    instanceInfoDB,
                                    taskDB, clusterStateDB,
                                    clusterResourcesDB,
                                    eventStore,
                                    communicator,
                                    eventBus, blacklistingAppMovementManager);
        val executor = executorHost(8080);
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


}