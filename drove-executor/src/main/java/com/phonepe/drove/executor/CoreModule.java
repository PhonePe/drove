package com.phonepe.drove.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.common.discovery.ZkConfig;
import com.phonepe.drove.common.discovery.ZkNodeDataStore;
import com.phonepe.drove.executor.engine.InstanceEngine;
import com.phonepe.drove.executor.resource.ResourceDB;
import io.dropwizard.setup.Environment;

import javax.inject.Singleton;

/**
 *
 */
public class CoreModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(NodeDataStore.class).to(ZkNodeDataStore.class);
    }

    @Provides
    @Singleton
    public InstanceEngine engine(final Environment environment, final Injector injector, final ResourceDB resourceDB) {
        return new InstanceEngine(environment.lifecycle()
                                          .executorService("instance-engine")
                                          .minThreads(128)
                                          .maxThreads(128)
                                          .build(),
                                  new InjectingInstanceActionFactory(injector),
                                  resourceDB);
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

}
