package com.phonepe.drove.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.common.discovery.ZkNodeDataStore;
import com.phonepe.drove.common.model.ControllerMessageType;
import com.phonepe.drove.common.model.controller.ControllerMessage;
import com.phonepe.drove.common.net.MessageSender;
import com.phonepe.drove.common.zookeeper.ZkConfig;
import com.phonepe.drove.executor.engine.InstanceEngine;
import com.phonepe.drove.executor.engine.RemoteControllerMessageSender;
import com.phonepe.drove.executor.managed.ExecutorIdManager;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceDB;
import io.dropwizard.setup.Environment;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;

import javax.inject.Singleton;

/**
 *
 */
public class ExecutorCoreModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(NodeDataStore.class).to(ZkNodeDataStore.class);
        bind(new TypeLiteral<MessageSender<ControllerMessageType, ControllerMessage>>(){})
                .to(RemoteControllerMessageSender.class);
    }


    @Provides
    @Singleton
    public InstanceEngine engine(
            final Environment environment,
            final Injector injector,
            final ResourceDB resourceDB,
            final ExecutorIdManager executorIdManager) {
        val executorService = environment.lifecycle()
                .executorService("instance-engine")
                .minThreads(128)
                .maxThreads(128)
                .build();
        return new InstanceEngine(
                executorIdManager,
                executorService,
                new InjectingInstanceActionFactory(injector),
                resourceDB
        );
    }

    @Provides
    @Singleton
    public ObjectMapper mapper(final Environment environment) {
        return environment.getObjectMapper();
    }

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
    public ResourceConfig resourceConfig(final AppConfig appConfig) {
        val resourceConfig = appConfig.getResources();
        return resourceConfig == null ? ResourceConfig.DEFAULT : resourceConfig;
    }
}
