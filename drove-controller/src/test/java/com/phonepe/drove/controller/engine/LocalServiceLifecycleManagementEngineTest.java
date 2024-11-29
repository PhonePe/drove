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

import com.codahale.metrics.SharedMetricRegistries;
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
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.DefaultInstanceScheduler;
import com.phonepe.drove.controller.resourcemgmt.InMemoryClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.InstanceScheduler;
import com.phonepe.drove.controller.statedb.*;
import com.phonepe.drove.controller.statemachine.localservice.LocalServiceActionContext;
import com.phonepe.drove.controller.testsupport.*;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.jobexecutor.JobExecutor;
import com.phonepe.drove.models.events.events.DroveLocalServiceStateChangeEvent;
import com.phonepe.drove.models.events.events.datatags.LocalServiceEventDataTag;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceSpec;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.LocalServiceOperation;
import com.phonepe.drove.models.operation.localserviceops.*;
import com.phonepe.drove.statemachine.Action;
import com.phonepe.drove.statemachine.ActionFactory;
import io.appform.functionmetrics.FunctionMetricsManager;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static com.phonepe.drove.controller.engine.ValidationStatus.SUCCESS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test for {@link LocalServiceLifecycleManagementEngine}
 */
@Slf4j
class LocalServiceLifecycleManagementEngineTest {

    @Inject
    LocalServiceStateDB localServiceStateDB;

    @Inject
    LocalServiceLifecycleManagementEngine engine;

    @Inject
    DroveEventBus droveEventBus;

    @Inject
    DummyExecutor executor;

    @BeforeAll
    public static void setupClass() {
        FunctionMetricsManager.initialize("com.phonepe.drove", SharedMetricRegistries.getOrCreate("test"));
    }


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
                bind(LocalServiceStateDB.class).annotatedWith(Names.named("StoredLocalServiceDB")).to(
                        InMemoryLocalServiceStateDB.class);
                bind(InstanceIdGenerator.class).to(RandomInstanceIdGenerator.class).asEagerSingleton();
                bind(ControllerRetrySpecFactory.class).to(DefaultControllerRetrySpecFactory.class);
                bind(ClusterResourcesDB.class).to(InMemoryClusterResourcesDB.class);
                bind(InstanceScheduler.class).to(DefaultInstanceScheduler.class);
                bind(ApplicationInstanceTokenManager.class).to(JWTApplicationInstanceTokenManager.class);
                bind(new TypeLiteral<ActionFactory<LocalServiceInfo, LocalServiceOperation, LocalServiceState,
                        LocalServiceActionContext,
                        Action<LocalServiceInfo, LocalServiceState, LocalServiceActionContext, LocalServiceOperation>>>() {
                }).to(InjectingLocalServiceActionFactory.class);
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
    void testLifecycleLocalService() {

        val spec = ControllerTestUtils.localServiceSpec();
        val serviceId = ControllerUtils.deployableObjectId(spec);

        val states = new HashSet<LocalServiceState>();

        setupStateRecorder(serviceId, states);
        createService(spec, serviceId);

        activateService(serviceId);
        sendCommand(serviceId,
                    new LocalServiceAdjustInstancesOperation(serviceId, null),
                    LocalServiceState.ADJUSTING_INSTANCES);
        waitForRequiredState(serviceId, LocalServiceState.ACTIVE);

        sendCommand(serviceId,
                    new LocalServiceRestartOperation(serviceId, false, null),
                    LocalServiceState.REPLACING_INSTANCES);
        waitForRequiredState(serviceId, LocalServiceState.ACTIVE);
        deactivateService(serviceId);

        sendCommand(serviceId,
                    new LocalServiceAdjustInstancesOperation(serviceId, null),
                    LocalServiceState.ADJUSTING_INSTANCES);
        waitForRequiredState(serviceId, LocalServiceState.INACTIVE);
        destroyService(serviceId);
        assertEquals(EnumSet.of(LocalServiceState.INACTIVE,
                                LocalServiceState.DEACTIVATION_REQUESTED,
                                LocalServiceState.ACTIVATION_REQUESTED,
                                LocalServiceState.ACTIVE,
                                LocalServiceState.ADJUSTING_INSTANCES,
                                LocalServiceState.REPLACING_INSTANCES,
                                LocalServiceState.DESTROY_REQUESTED,
                                LocalServiceState.DESTROYED), states);
    }

    private void setupStateRecorder(String appId, Set<LocalServiceState> states) {
        droveEventBus.onNewEvent().connect(e -> {
            if (e instanceof DroveLocalServiceStateChangeEvent asc) {
                val metadata = asc.getMetadata();
                if (metadata.get(LocalServiceEventDataTag.LOCAL_SERVICE_ID).equals(appId)) {
                    states.add((LocalServiceState) metadata.get(LocalServiceEventDataTag.CURRENT_STATE));
                }
            }
        });
    }


    private void createService(LocalServiceSpec spec, String serviceId) {
        sendCommand(serviceId, new LocalServiceCreateOperation(spec, 1), LocalServiceState.INACTIVE);
    }

    private void activateService(String serviceId) {
        sendCommand(serviceId, new LocalServiceActivateOperation(serviceId), LocalServiceState.ACTIVE);
    }

    private void deactivateService(String serviceId) {
        sendCommand(serviceId, new LocalServiceDeactivateOperation(serviceId), LocalServiceState.INACTIVE);
    }

    private void destroyService(String serviceId) {
        val res = engine.handleOperation(new LocalServiceDestroyOperation(serviceId));
        assertEquals(SUCCESS, res.getStatus(), Joiner.on(",").join(res.getMessages()));
        await().atMost(Duration.ofSeconds(30)).until(() -> !engine.exists(serviceId));
    }

    private void waitForRequiredState(String serviceId, LocalServiceState required) {
        CommonTestUtils.waitUntil(() -> required.equals(engine.currentState(serviceId)
                                                                                .orElse(LocalServiceState.DESTROYED)),
                                  Duration.ofSeconds(35));
    }

    private void sendCommand(
            String serviceId,
            LocalServiceOperation operation,
            LocalServiceState requiredState) {
        val res = engine.handleOperation(operation);
        assertEquals(SUCCESS, res.getStatus(), Joiner.on(",").join(res.getMessages()));
       waitForRequiredState(serviceId, requiredState);
    }
}