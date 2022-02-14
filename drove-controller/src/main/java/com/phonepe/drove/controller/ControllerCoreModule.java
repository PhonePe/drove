package com.phonepe.drove.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.phonepe.drove.common.ActionFactory;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.common.discovery.ZkNodeDataStore;
import com.phonepe.drove.common.model.ExecutorMessageType;
import com.phonepe.drove.common.model.executor.ExecutorMessage;
import com.phonepe.drove.common.net.MessageSender;
import com.phonepe.drove.common.zookeeper.ZkConfig;
import com.phonepe.drove.controller.engine.InjectingAppActionFactory;
import com.phonepe.drove.controller.engine.RemoteExecutorMessageSender;
import com.phonepe.drove.controller.jobexecutor.JobExecutor;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.DefaultInstanceScheduler;
import com.phonepe.drove.controller.resourcemgmt.InstanceScheduler;
import com.phonepe.drove.controller.resourcemgmt.MapBasedClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.ExecutorStateDB;
import com.phonepe.drove.controller.statedb.MapBasedApplicationStateDB;
import com.phonepe.drove.controller.statedb.MapBasedExecutorStateDB;
import com.phonepe.drove.controller.statemachine.AppAction;
import com.phonepe.drove.controller.statemachine.AppActionContext;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import io.dropwizard.setup.Environment;
import org.apache.curator.framework.CuratorFramework;

import javax.inject.Singleton;
import java.util.Objects;

/**
 *
 */
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
                                         .maxThreads(1024)
                                         .minThreads(1024)
                                         .build());
    }

    @Provides
    @Singleton
    public ObjectMapper mapper(final Environment environment) {
        return environment.getObjectMapper();
    }

    @Override
    protected void configure() {
        bind(NodeDataStore.class).to(ZkNodeDataStore.class);
        bind(ExecutorStateDB.class).to(MapBasedExecutorStateDB.class);
        bind(ClusterResourcesDB.class).to(MapBasedClusterResourcesDB.class);
        bind(ApplicationStateDB.class).to(MapBasedApplicationStateDB.class);
        bind(InstanceScheduler.class).to(DefaultInstanceScheduler.class);
        bind(new TypeLiteral<MessageSender<ExecutorMessageType, ExecutorMessage>>() {})
                .to(RemoteExecutorMessageSender.class);
        bind(new TypeLiteral<ActionFactory<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext, AppAction>>(){})
                .to(InjectingAppActionFactory.class);
    }

    @Provides
    @Singleton
    public ClusterAuthenticationConfig clusterAuth(final AppConfig appConfig) {
        return Objects.requireNonNullElse(appConfig.getClusterAuth(), ClusterAuthenticationConfig.DEFAULT);
    }
}
