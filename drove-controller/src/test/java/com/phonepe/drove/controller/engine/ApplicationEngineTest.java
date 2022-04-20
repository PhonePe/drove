package com.phonepe.drove.controller.engine;

import com.google.common.base.Joiner;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.phonepe.drove.common.ActionFactory;
import com.phonepe.drove.common.model.ExecutorMessageType;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.executor.ExecutorMessage;
import com.phonepe.drove.common.net.MessageSender;
import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.event.events.DroveAppStateChangeEvent;
import com.phonepe.drove.controller.jobexecutor.JobExecutor;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.controller.resourcemgmt.*;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.ExecutorStateDB;
import com.phonepe.drove.controller.statedb.InstanceInfoDB;
import com.phonepe.drove.controller.statedb.MapBasedExecutorStateDB;
import com.phonepe.drove.controller.statemachine.AppAction;
import com.phonepe.drove.controller.statemachine.AppActionContext;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.application.PortType;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.ops.*;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static com.phonepe.drove.controller.engine.CommandValidator.ValidationStatus.SUCCESS;
import static com.phonepe.drove.controller.utils.ControllerUtils.appId;
import static com.phonepe.drove.models.application.ApplicationState.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
@Slf4j
class ApplicationEngineTest extends ControllerTestBase {

    @Inject
    ClusterResourcesDB clusterResourcesDB;
    @Inject
    InstanceInfoDB instanceInfoDB;
    @Inject
    DroveEventBus droveEventBus;
    @Inject
    InstanceIdGenerator instanceIdGenerator;
    @Inject
    ApplicationEngine engine;

    @BeforeEach
    public void setup() {
        val injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ApplicationStateDB.class).to(InMemoryApplicationStateDB.class).asEagerSingleton();
                bind(InstanceInfoDB.class).to(InMemoryInstanceInfoDB.class).asEagerSingleton();
                bind(InstanceIdGenerator.class).to(FixedInstanceIdGenerator.class);
                bind(ControllerRetrySpecFactory.class).to(DefaultControllerRetrySpecFactory.class);
                bind(ExecutorStateDB.class).to(MapBasedExecutorStateDB.class);
                bind(ClusterResourcesDB.class).to(MapBasedClusterResourcesDB.class);
                bind(InstanceScheduler.class).to(DefaultInstanceScheduler.class);
                bind(new TypeLiteral<ActionFactory<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext, AppAction>>() {
                })
                        .to(InjectingAppActionFactory.class);
            }

            @Provides
            @Singleton
            public LeadershipEnsurer leadershipEnsurer() {
                val l = mock(LeadershipEnsurer.class);
                when(l.isLeader()).thenReturn(true);
                return l;
            }

            @Provides
            @Singleton
            @SuppressWarnings("unchecked")
            public MessageSender<ExecutorMessageType, ExecutorMessage> executorMessageSender() {
                val ems = (MessageSender<ExecutorMessageType, ExecutorMessage>) mock(MessageSender.class);
                when(ems.send(any(ExecutorMessage.class)))
                        .thenAnswer((Answer<MessageResponse>) invocationOnMock
                                -> new MessageResponse(((ExecutorMessage) invocationOnMock.getArgument(0)).getHeader(),
                                                       MessageDeliveryStatus.ACCEPTED));
                return ems;
            }

            @Provides
            @Singleton
            public JobExecutor<Boolean> jobExecutor() {
                return new JobExecutor<>(Executors.newSingleThreadExecutor());
            }

        });
        injector.injectMembers(this);
    }

    @Test
    void testLifecycleAppBasicStartStop() {

        val spec = ControllerTestUtils.appSpec();
        val appId = appId(spec);


        val executor = ControllerTestUtils.executorHost(8080);
        clusterResourcesDB.update(List.of(executor.getNodeData()));
        val allocatedExecutorNode = ControllerTestUtils.allocatedExecutorNode(8080);
        val instanceId = instanceIdGenerator.generate();
        val instanceInfo = createInstanceInfo(spec, appId, allocatedExecutorNode, instanceId);
        val states = new HashSet<ApplicationState>();
        droveEventBus.onNewEvent().connect(e -> {
            if (e instanceof DroveAppStateChangeEvent asc) {
                if (asc.getAppId().equals(appId)) {
                    states.add(asc.getState());
                }
            }
        });
        createApp(spec, appId);

        startInstance(appId, instanceId, instanceInfo);
        sendCommand(appId, new ApplicationSuspendOperation(appId, ClusterOpSpec.DEFAULT), MONITORING,
                    () -> instanceInfoDB.updateInstanceState(appId,
                                                             instanceId,
                                                             createInstanceInfo(instanceInfo,
                                                                                InstanceState.STOPPED)));
        destryoyApp(appId);
        log.info("States: {}", states);
        assertEquals(EnumSet.of(MONITORING,
                                SCALING_REQUESTED,
                                RUNNING,
                                DESTROY_REQUESTED,
                                DESTROYED), states);
    }


    @Test
    void testLifecycleApp() {
        val spec = ControllerTestUtils.appSpec();
        val appId = appId(spec);


        val executor = ControllerTestUtils.executorHost(8080);
        clusterResourcesDB.update(List.of(executor.getNodeData()));
        val allocatedExecutorNode = ControllerTestUtils.allocatedExecutorNode(8080);
        val instanceId = instanceIdGenerator.generate();
        val instanceInfo = createInstanceInfo(spec, appId, allocatedExecutorNode, instanceId);
        val states = new HashSet<ApplicationState>();
        droveEventBus.onNewEvent().connect(e -> {
            if (e instanceof DroveAppStateChangeEvent asc) {
                if (asc.getAppId().equals(appId)) {
                    states.add(asc.getState());
                }
            }
        });
        createApp(spec, appId);
        startInstance(appId, instanceId, instanceInfo);
        sendCommand(appId,
                    new ApplicationStopInstancesOperation(appId, List.of(instanceId), true, ClusterOpSpec.DEFAULT),
                    MONITORING,
                    () -> instanceInfoDB.updateInstanceState(appId,
                                                             instanceId,
                                                             createInstanceInfo(instanceInfo,
                                                                                InstanceState.STOPPED)));
        destryoyApp(appId);

        log.info("States: {}", states);
        assertEquals(EnumSet.of(MONITORING,
                                SCALING_REQUESTED,
                                RUNNING,
                                STOP_INSTANCES_REQUESTED,
                                DESTROY_REQUESTED,
                                DESTROYED), states);
    }

    @Test
    void testAppRecovery() {
        val spec = ControllerTestUtils.appSpec();
        val appId = appId(spec);


        val executor = ControllerTestUtils.executorHost(8080);
        clusterResourcesDB.update(List.of(executor.getNodeData()));
        val allocatedExecutorNode = ControllerTestUtils.allocatedExecutorNode(8080);
        val instanceId = instanceIdGenerator.generate();
        val instanceInfo = createInstanceInfo(spec, appId, allocatedExecutorNode, instanceId);
        val states = new HashSet<ApplicationState>();
        droveEventBus.onNewEvent().connect(e -> {
            if (e instanceof DroveAppStateChangeEvent asc) {
                if (asc.getAppId().equals(appId)) {
                    states.add(asc.getState());
                }
            }
        });
        createApp(spec, appId);
        startInstance(appId, instanceId, instanceInfo);
        clusterResourcesDB.update(List.of(executor.getNodeData()));
        instanceInfoDB.deleteInstanceState(appId, instanceId);
        sendCommand(appId, new ApplicationRecoverOperation(appId), RUNNING,
                    () -> instanceInfoDB.updateInstanceState(appId, instanceId, instanceInfo));
        sendCommand(appId, new ApplicationSuspendOperation(appId, ClusterOpSpec.DEFAULT), MONITORING,
                    () -> instanceInfoDB.updateInstanceState(appId,
                                                             instanceId,
                                                             createInstanceInfo(instanceInfo,
                                                                                InstanceState.STOPPED)));
        destryoyApp(appId);
        log.info("States: {}", states);
        assertEquals(EnumSet.of(MONITORING,
                                SCALING_REQUESTED,
                                RUNNING,
                                OUTAGE_DETECTED,
                                DESTROY_REQUESTED,
                                DESTROYED), states);
        {
        }
    }

    private void startInstance(String appId, String instanceId, InstanceInfo instanceInfo) {
        sendCommand(appId, new ApplicationStartInstancesOperation(appId, 1, ClusterOpSpec.DEFAULT), RUNNING,
                    () -> instanceInfoDB.updateInstanceState(appId, instanceId, instanceInfo));
    }


    private void createApp(ApplicationSpec spec, String appId) {
        sendCommand(appId, new ApplicationCreateOperation(spec, 0, ClusterOpSpec.DEFAULT), MONITORING,
                    () -> {});
    }
    private void destryoyApp(String appId) {
        val res = engine.handleOperation(new ApplicationDestroyOperation(appId, ClusterOpSpec.DEFAULT));
        assertEquals(SUCCESS, res.getStatus(), Joiner.on(",").join(res.getMessages()));
        await().atMost(Duration.ofSeconds(30)).until(() -> !engine.exists(appId));
    }

    private static final class FixedInstanceIdGenerator implements InstanceIdGenerator {
        @Override
        public String generate() {
            return "TEST_INSTANCE_1";
        }
    }

    private static final class InMemoryApplicationStateDB implements ApplicationStateDB {

        private final Map<String, ApplicationInfo> apps = new ConcurrentHashMap<>();

        @Override
        public List<ApplicationInfo> applications(int start, int size) {
            return List.copyOf(apps.values());
        }

        @Override
        public Optional<ApplicationInfo> application(String appId) {
            return Optional.ofNullable(apps.get(appId));
        }

        @Override
        public boolean updateApplicationState(String appId, ApplicationInfo applicationInfo) {
            return apps.compute(appId, (id, old) -> applicationInfo) != null;
        }

        @Override
        public boolean deleteApplicationState(String appId) {
            return apps.remove(appId) != null;
        }
    }

    private static final class InMemoryInstanceInfoDB implements InstanceInfoDB {

        private final Map<String, Map<String, InstanceInfo>> instances = new ConcurrentHashMap<>();

        @Override
        public List<InstanceInfo> activeInstances(String appId, Set<InstanceState> validStates, int start, int size) {
            return instances.getOrDefault(appId, Collections.emptyMap())
                    .values()
                    .stream()
                    .filter(i -> validStates.contains(i.getState()))
                    .toList();
        }

        @Override
        public Optional<InstanceInfo> instance(String appId, String instanceId) {
            return Optional.ofNullable(instances.getOrDefault(appId, Collections.emptyMap()).get(instanceId));
        }

        @Override
        public boolean updateInstanceState(String appId, String instanceId, InstanceInfo instanceInfo) {
            instances.compute(appId, (aId, old) -> {
                val ins = Objects.requireNonNullElse(old, new ConcurrentHashMap<String, InstanceInfo>());
                ins.put(instanceId, instanceInfo);
                return ins;
            });
            return true;
        }

        @Override
        public boolean deleteInstanceState(String appId, String instanceId) {
            return !instances.containsKey(appId) || instances.get(appId).remove(instanceId) != null;
        }

        @Override
        public boolean deleteAllInstancesForApp(String appId) {
            return instances.remove(appId) != null;
        }

        @Override
        public long markStaleInstances(String appId) {
            return 0;
        }

        @Override
        public List<InstanceInfo> oldInstances(String appId, int start, int size) {
            return Collections.emptyList();
        }
    }

    private InstanceInfo createInstanceInfo(
            ApplicationSpec spec,
            String appId,
            AllocatedExecutorNode allocatedExecutorNode,
            String instanceId) {
        return new InstanceInfo(appId,
                                spec.getName(),
                                instanceId,
                                allocatedExecutorNode.getExecutorId(),
                                new LocalInstanceInfo(allocatedExecutorNode.getHostname(),
                                                      Collections.singletonMap("main",
                                                                               new InstancePort(
                                                                                       8000,
                                                                                       32000,
                                                                                       PortType.HTTP))),
                                List.of(allocatedExecutorNode.getCpu(),
                                        allocatedExecutorNode.getMemory()),
                                InstanceState.HEALTHY,
                                Collections.emptyMap(),
                                null,
                                new Date(),
                                new Date());
    }

    private InstanceInfo createInstanceInfo(
            final InstanceInfo from, final InstanceState newState) {
        return new InstanceInfo(from.getAppId(),
                                from.getAppName(),
                                from.getInstanceId(),
                                from.getExecutorId(),
                                from.getLocalInfo(),
                                from.getResources(),
                                newState,
                                from.getMetadata(),
                                from.getErrorMessage(),
                                from.getCreated(),
                                new Date());
    }

    private void sendCommand(
            String appId,
            ApplicationOperation operation,
            ApplicationState requiredState,
            Runnable updater) {
        val res = engine.handleOperation(operation);
        assertEquals(SUCCESS, res.getStatus(), Joiner.on(",").join(res.getMessages()));
        await().pollDelay(Duration.ofSeconds(3))
                .forever()
                .conditionEvaluationListener(evaluatedCondition -> updater.run())
                .until(() -> requiredState.equals(engine.applicationState(appId).orElse(ApplicationState.FAILED)));
    }


}