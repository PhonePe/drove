/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.phonepe.drove.auth.config.ApplicationAuthConfig;
import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.auth.core.ApplicationInstanceTokenManager;
import com.phonepe.drove.auth.core.JWTApplicationInstanceTokenManager;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.common.discovery.ZkNodeDataStore;
import com.phonepe.drove.common.model.ExecutorMessageType;
import com.phonepe.drove.common.model.executor.ExecutorMessage;
import com.phonepe.drove.common.net.MessageSender;
import com.phonepe.drove.common.zookeeper.ZkConfig;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.engine.*;
import com.phonepe.drove.controller.event.EventStore;
import com.phonepe.drove.controller.event.InMemoryEventStore;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.DefaultInstanceScheduler;
import com.phonepe.drove.controller.resourcemgmt.InMemoryClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.InstanceScheduler;
import com.phonepe.drove.controller.statedb.*;
import com.phonepe.drove.controller.statemachine.applications.AppAction;
import com.phonepe.drove.controller.statemachine.applications.AppActionContext;
import com.phonepe.drove.jobexecutor.JobExecutor;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.deploy.FailureStrategy;
import com.phonepe.drove.statemachine.ActionFactory;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.Duration;
import org.apache.curator.framework.CuratorFramework;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;

/**
 *
 */
@SuppressWarnings("unused")
public class ControllerCoreModule extends AbstractModule {
    @Provides
    @Singleton
    public ZkConfig zkConfig(final AppConfig appConfig) {
        return appConfig.getZookeeper();
    }

    @Provides
    @Singleton
    public CuratorFramework curator(ZkConfig config) {
        return CommonUtils.buildCurator(config);
    }

    @Provides
    @Singleton
    public JobExecutor<Boolean> jobExecutor(final Environment environment) {
        return new JobExecutor<>(environment.lifecycle()
                                         .executorService("job-executor-%d")
                                         .maxThreads(Integer.MAX_VALUE)
                                         .minThreads(0)
                                         .workQueue(new SynchronousQueue<>())
                                         .keepAliveTime(Duration.seconds(60))
                                         .build());
    }

    @Provides
    @Singleton
    @Named("MonitorThreadPool")
    public ExecutorService monitorExecutor(final Environment environment) {
        return environment.lifecycle().executorService("application-monitor-%d")
                .maxThreads(Integer.MAX_VALUE)
                .minThreads(0)
                .workQueue(new SynchronousQueue<>())
                .keepAliveTime(Duration.seconds(60))
                .build();
    }

    @Provides
    @Singleton
    @Named("BlacklistingAppMover")
    public ExecutorService blacklistingAppMovement(final Environment environment) {
        return environment.lifecycle().executorService("blacklisting-instance-mover-%d")
                .maxThreads(Integer.MAX_VALUE)
                .minThreads(0)
                .workQueue(new SynchronousQueue<>())
                .keepAliveTime(Duration.seconds(60))
                .build();
    }

    @Provides
    @Singleton
    @Named("TaskThreadPool")
    public ExecutorService taskThreadPool(final Environment environment) {
        return environment.lifecycle().executorService("task-runner-%d")
                .maxThreads(Integer.MAX_VALUE)
                .minThreads(0)
                .workQueue(new SynchronousQueue<>())
                .keepAliveTime(Duration.seconds(60))
                .build();
    }

    @Provides
    @Singleton
    @Named("JobLevelThreadFactory")
    public ThreadFactory jobLevelThreadFactory() {
        return new ThreadFactoryBuilder().setNameFormat("job-level-%d").build();
    }

    @Provides
    @Singleton
    public CloseableHttpClient httpClient() {
        return CommonUtils.createHttpClient(false);
    }

    @Provides
    @Singleton
    @Named("insecure")
    public CloseableHttpClient insecureHttpClient() {
        return CommonUtils.createHttpClient(true);
    }

    @Provides
    @Singleton
    public ObjectMapper mapper(final Environment environment) {
        return environment.getObjectMapper();
    }

    @Override
    protected void configure() {
        bind(NodeDataStore.class).to(ZkNodeDataStore.class);
        bind(ClusterResourcesDB.class).to(InMemoryClusterResourcesDB.class);
        bind(ApplicationStateDB.class).to(CachingProxyApplicationStateDB.class);
        bind(ApplicationStateDB.class).annotatedWith(Names.named("StoredApplicationStateDB"))
                .to(ZkApplicationStateDB.class);
        bind(ApplicationInstanceInfoDB.class).to(CachingProxyApplicationInstanceInfoDB.class);
        bind(ApplicationInstanceInfoDB.class).annotatedWith(Names.named("StoredInstanceInfoDB")).to(
                ZkApplicationInstanceInfoDB.class);
        bind(TaskDB.class).to(CachingProxyTaskDB.class);
        bind(TaskDB.class).annotatedWith(Names.named("StoredTaskDB")).to(ZkTaskDB.class);
        bind(ClusterStateDB.class).to(CachingProxyClusterStateDB.class);
        bind(ClusterStateDB.class).annotatedWith(Names.named("StoredClusterStateDB")).to(ZkClusterStateDB.class);
        bind(EventStore.class).to(InMemoryEventStore.class);
        bind(InstanceScheduler.class).to(DefaultInstanceScheduler.class);
        bind(InstanceIdGenerator.class).to(RandomInstanceIdGenerator.class);
        bind(ApplicationInstanceTokenManager.class).to(JWTApplicationInstanceTokenManager.class);
        bind(new TypeLiteral<MessageSender<ExecutorMessageType, ExecutorMessage>>() {
        })
                .to(RemoteExecutorMessageSender.class);
        bind(new TypeLiteral<ActionFactory<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext,
                AppAction>>() {
        }).to(InjectingAppActionFactory.class);
        bind(ControllerRetrySpecFactory.class).to(DefaultControllerRetrySpecFactory.class);
    }

    @Provides
    @Singleton
    public ClusterAuthenticationConfig clusterAuth(final AppConfig appConfig) {
        return Objects.requireNonNullElse(appConfig.getClusterAuth(), ClusterAuthenticationConfig.DEFAULT);
    }

    @Provides
    @Singleton
    public ApplicationAuthConfig applicationAuthConfig(final AppConfig appConfig) {
        return Objects.requireNonNullElse(appConfig.getInstanceAuth(), ApplicationAuthConfig.DEFAULT);
    }

    @Provides
    @Singleton
    public ControllerOptions options(final AppConfig config) {
        return Objects.requireNonNullElse(config.getOptions(), ControllerOptions.DEFAULT);
    }

    @Provides
    @Singleton
    public ClusterOpSpec defaultClusterOpSpec(final ControllerOptions controllerOptions) {
        return new ClusterOpSpec(Objects.requireNonNullElse(controllerOptions.getClusterOpTimeout(),
                                                            ClusterOpSpec.DEFAULT_CLUSTER_OP_TIMEOUT),
                                 Math.max(controllerOptions.getClusterOpParallelism(),
                                          ClusterOpSpec.DEFAULT_CLUSTER_OP_PARALLELISM),
                                 FailureStrategy.STOP);
    }
}
