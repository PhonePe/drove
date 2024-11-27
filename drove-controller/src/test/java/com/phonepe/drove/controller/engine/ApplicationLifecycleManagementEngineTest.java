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

package com.phonepe.drove.controller.engine;

import com.google.common.base.Joiner;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.phonepe.drove.auth.config.ApplicationAuthConfig;
import com.phonepe.drove.auth.core.ApplicationInstanceTokenManager;
import com.phonepe.drove.auth.core.JWTApplicationInstanceTokenManager;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.common.model.ExecutorMessageType;
import com.phonepe.drove.common.model.executor.ExecutorMessage;
import com.phonepe.drove.common.net.HttpCaller;
import com.phonepe.drove.common.net.MessageSender;
import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.DefaultInstanceScheduler;
import com.phonepe.drove.controller.resourcemgmt.InMemoryClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.InstanceScheduler;
import com.phonepe.drove.controller.statedb.*;
import com.phonepe.drove.controller.statemachine.applications.AppActionContext;
import com.phonepe.drove.controller.testsupport.*;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.jobexecutor.JobExecutor;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.events.events.DroveAppStateChangeEvent;
import com.phonepe.drove.models.events.events.datatags.AppEventDataTag;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.deploy.FailureStrategy;
import com.phonepe.drove.models.operation.ops.*;
import com.phonepe.drove.statemachine.Action;
import com.phonepe.drove.statemachine.ActionFactory;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;

import static com.phonepe.drove.controller.engine.ValidationStatus.FAILURE;
import static com.phonepe.drove.controller.engine.ValidationStatus.SUCCESS;
import static com.phonepe.drove.models.application.ApplicationState.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
@Slf4j
class ApplicationLifecycleManagementEngineTest extends ControllerTestBase {

    private static final ClusterOpSpec TEST_STRATEGY = new ClusterOpSpec(io.dropwizard.util.Duration.seconds(120),
                                                                         1,
                                                                         FailureStrategy.STOP);

    @Inject
    ApplicationInstanceInfoDB instanceInfoDB;
    @Inject
    DroveEventBus droveEventBus;

    @Inject
    DummyExecutor executor;

    @Inject
    ApplicationLifecycleManagementEngine engine;

    @BeforeEach
    public void setup() {
        val injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ApplicationStateDB.class).to(CachingProxyApplicationStateDB.class);
                bind(ApplicationStateDB.class)
                        .annotatedWith(Names.named("StoredApplicationStateDB"))
                        .to(InMemoryApplicationStateDB.class);
                bind(ApplicationInstanceInfoDB.class).to(CachingProxyApplicationInstanceInfoDB.class);
                bind(ApplicationInstanceInfoDB.class)
                        .annotatedWith(Names.named("StoredInstanceInfoDB"))
                        .to(InMemoryApplicationInstanceInfoDB.class);
                bind(TaskDB.class).to(CachingProxyTaskDB.class);
                bind(TaskDB.class).annotatedWith(Names.named("StoredTaskDB")).to(InMemoryTaskDB.class);
                bind(LocalServiceStateDB.class).to(CachingProxyLocalServiceStateDB.class);
                bind(LocalServiceStateDB.class).annotatedWith(Names.named("StoredLocalServiceDB")).to(InMemoryLocalServiceStateDB.class);
                bind(InstanceIdGenerator.class).to(RandomInstanceIdGenerator.class).asEagerSingleton();
                bind(ControllerRetrySpecFactory.class).to(DefaultControllerRetrySpecFactory.class);
                bind(ClusterResourcesDB.class).to(InMemoryClusterResourcesDB.class);
                bind(InstanceScheduler.class).to(DefaultInstanceScheduler.class);
                bind(ApplicationInstanceTokenManager.class).to(JWTApplicationInstanceTokenManager.class);
                bind(new TypeLiteral<ActionFactory<ApplicationInfo, ApplicationOperation, ApplicationState,
                        AppActionContext, Action<ApplicationInfo, ApplicationState, AppActionContext, ApplicationOperation>>>() {
                }).to(InjectingAppActionFactory.class);
                bind(new TypeLiteral<MessageSender<ExecutorMessageType, ExecutorMessage>>() {
                })
                        .to(DummyExecutorMessageSender.class).asEagerSingleton();
            }

            @Provides
            @Singleton
            public LeadershipEnsurer leadershipEnsurer() {
                val l = mock(LeadershipEnsurer.class);
                when(l.isLeader()).thenReturn(true);
                when(l.onLeadershipStateChanged()).thenReturn(new ConsumingSyncSignal<>());
                return l;
            }


            @Provides
            @Singleton
            public JobExecutor<Boolean> jobExecutor() {
                return new JobExecutor<>(Executors.newCachedThreadPool());
            }


            @Provides
            @Singleton
            @Named("MonitorThreadPool")
            public ExecutorService monitorExecutor() {
                return Executors.newCachedThreadPool();
            }

            @Provides
            @Singleton
            @Named("JobLevelThreadFactory")
            public ThreadFactory jobLevelThreadFactory() {
                return new ThreadFactoryBuilder().setNameFormat("job-level-%d").build();
            }

            @Provides
            @Singleton
            public ApplicationAuthConfig applicationAuthConfig() {
                return ApplicationAuthConfig.DEFAULT;
            }

            @Provides
            @Singleton
            public ClusterOpSpec clusterOpSpec() {
                return ControllerTestUtils.DEFAULT_CLUSTER_OP;
            }

            @Provides
            @Singleton
            public ControllerOptions controllerOptions() {
                return ControllerOptions.DEFAULT;
            }

            @Provides
            @Singleton
            public HttpCaller httpCaller() {
                return CommonTestUtils.httpCaller();
            }
        });
        injector.injectMembers(this);
        executor.start();
    }

    @AfterEach
    public void destroy() {
        executor.stop();
    }

    @Test
    void testLifecycleAppBasicStartStop() {

        val spec = ControllerTestUtils.appSpec();
        val appId = ControllerUtils.deployableObjectId(spec);

        val states = new HashSet<ApplicationState>();

        setupStateRecorder(appId, states);
        createApp(spec, appId);

        startInstance(appId);
        suspendApp(appId);
        destroyApp(appId);
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
        val appId = ControllerUtils.deployableObjectId(spec);

        val states = new HashSet<ApplicationState>();

        setupStateRecorder(appId, states);
        createApp(spec, appId);
        startInstance(appId);
        val instanceId = getSingleInstanceId(appId);
        assertNotNull(instanceId);
        sendCommand(appId,
                    new ApplicationStopInstancesOperation(appId, List.of(instanceId), true, TEST_STRATEGY),
                    MONITORING
                   );
        destroyApp(appId);

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
        val appId = ControllerUtils.deployableObjectId(spec);

        val states = new HashSet<ApplicationState>();

        setupStateRecorder(appId, states);
        createApp(spec, appId);
        startInstance(appId);
        val instanceId = getSingleInstanceId(appId);
        assertNotNull(instanceId);
        instanceInfoDB.deleteInstanceState(appId, instanceId);
        executor.dropAppInstance(instanceId);
        sendCommand(appId, new ApplicationRecoverOperation(appId), RUNNING);
        suspendApp(appId);
        destroyApp(appId);
        log.info("States: {}", states);
        assertEquals(EnumSet.of(MONITORING,
                                SCALING_REQUESTED,
                                RUNNING,
                                OUTAGE_DETECTED,
                                DESTROY_REQUESTED,
                                DESTROYED), states);
    }


    @Test
    void testAppRestart() {
        val spec = ControllerTestUtils.appSpec();
        val appId = ControllerUtils.deployableObjectId(spec);


        val states = new LinkedHashSet<ApplicationState>();
        setupStateRecorder(appId, states);
        createApp(spec, appId);
        sendCommand(appId, new ApplicationStartInstancesOperation(appId, 1, TEST_STRATEGY), RUNNING);
        sendCommand(appId, new ApplicationReplaceInstancesOperation(appId, Set.of(), false, TEST_STRATEGY), RUNNING);
        sendCommand(appId,
                    new ApplicationReplaceInstancesOperation(appId,
                                                             instanceInfoDB.healthyInstances(appId)
                                                                     .stream()
                                                                     .map(InstanceInfo::getInstanceId)
                                                                     .collect(Collectors.toUnmodifiableSet()),
                                                             false, TEST_STRATEGY),
                    RUNNING);
        sendCommand(appId, new ApplicationSuspendOperation(appId, TEST_STRATEGY), MONITORING);
        destroyApp(appId);
        log.info("States: {}", states);
        assertEquals(EnumSet.of(MONITORING,
                                SCALING_REQUESTED,
                                RUNNING,
                                REPLACE_INSTANCES_REQUESTED,
                                DESTROY_REQUESTED,
                                DESTROYED), states);
    }

    @Test
    void testRestartWithWrongInstanceIds() {
        val spec = ControllerTestUtils.appSpec();
        val appId = ControllerUtils.deployableObjectId(spec);


        val states = new LinkedHashSet<ApplicationState>();
        setupStateRecorder(appId, states);
        createApp(spec, appId);
        sendCommand(appId, new ApplicationStartInstancesOperation(appId, 1, TEST_STRATEGY), RUNNING);
        val res = engine.handleOperation(new ApplicationReplaceInstancesOperation(appId, Set.of("a123"),
                                                                                  false,
                                                                                  TEST_STRATEGY));
        assertEquals(FAILURE, res.getStatus(), Joiner.on(",").join(res.getMessages()));
        assertEquals("There are no replaceable healthy instances with ids: [a123]", res.getMessages().get(0));
//        sendCommand(appId, new ApplicationReplaceInstancesOperation(appId, Set.of("a123"), TEST_STRATEGY), RUNNING);
        log.info("SUSPENDING APP");
        sendCommand(appId, new ApplicationSuspendOperation(appId, TEST_STRATEGY), MONITORING);
        destroyApp(appId);
    }

    private void setupStateRecorder(String appId, Set<ApplicationState> states) {
        droveEventBus.onNewEvent().connect(e -> {
            if (e instanceof DroveAppStateChangeEvent asc) {
                val metadata = asc.getMetadata();
                if (metadata.get(AppEventDataTag.APP_ID).equals(appId)) {
                    states.add((ApplicationState) metadata.get(AppEventDataTag.CURRENT_STATE));
                }
            }
        });
    }


    private String getSingleInstanceId(String appId) {
        return instanceInfoDB.activeInstances(appId, 0, Integer.MAX_VALUE)
                .stream()
                .findFirst()
                .map(InstanceInfo::getInstanceId)
                .orElse(null);
    }

    private void suspendApp(String appId) {
        sendCommand(appId, new ApplicationSuspendOperation(appId, TEST_STRATEGY), MONITORING);
    }

    private void startInstance(String appId) {
        sendCommand(appId, new ApplicationStartInstancesOperation(appId, 1, TEST_STRATEGY), RUNNING);
    }


    private void createApp(ApplicationSpec spec, String appId) {
        sendCommand(appId, new ApplicationCreateOperation(spec, 0, TEST_STRATEGY), MONITORING);
    }

    private void destroyApp(String appId) {
        val res = engine.handleOperation(new ApplicationDestroyOperation(appId, TEST_STRATEGY));
        assertEquals(SUCCESS, res.getStatus(), Joiner.on(",").join(res.getMessages()));
        await().atMost(Duration.ofSeconds(30)).until(() -> !engine.exists(appId));
    }

    private void sendCommand(
            String appId,
            ApplicationOperation operation,
            ApplicationState requiredState) {
        val res = engine.handleOperation(operation);
        assertEquals(SUCCESS, res.getStatus(), Joiner.on(",").join(res.getMessages()));
        try {
            await().pollDelay(Duration.ofSeconds(3))
                    .atMost(Duration.ofSeconds(10))
                    .until(() -> requiredState.equals(engine.currentState(appId).orElse(ApplicationState.FAILED)));
        } catch (ConditionTimeoutException e) {
            log.warn("Timeout waiting for state: {}", requiredState);
            throw e;
        }
    }


}