package com.phonepe.drove.executor;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.common.discovery.ZkNodeDataStore;
import com.phonepe.drove.common.model.ControllerMessageType;
import com.phonepe.drove.common.model.controller.ControllerMessage;
import com.phonepe.drove.common.net.MessageSender;
import com.phonepe.drove.common.zookeeper.ZkConfig;
import com.phonepe.drove.executor.dockerauth.DockerAuthConfig;
import com.phonepe.drove.executor.engine.InstanceEngine;
import com.phonepe.drove.executor.engine.RemoteControllerMessageSender;
import com.phonepe.drove.executor.logging.LogInfo;
import com.phonepe.drove.executor.managed.ExecutorIdManager;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.statemachine.BlacklistingManager;
import io.dropwizard.setup.Environment;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;

import javax.inject.Singleton;
import java.net.URI;
import java.util.Objects;

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
            final ResourceManager resourceDB,
            final ExecutorIdManager executorIdManager,
            final BlacklistingManager blacklistManager,
            final DockerClient client) {
        val executorService = environment.lifecycle()
                .executorService("instance-engine")
                .minThreads(128)
                .maxThreads(128)
                .build();
        return new InstanceEngine(
                executorIdManager,
                executorService,
                new InjectingInstanceActionFactory(injector),
                resourceDB,
                blacklistManager,
                client);
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

    @Provides
    @Singleton
    public MetricRegistry registry(final Environment environment) {
        return environment.metrics();
    }

    @Provides
    @Singleton
    public DockerClient dockerClient() {
        return DockerClientImpl.getInstance(DefaultDockerClientConfig.createDefaultConfigBuilder()
                                                    .build(),
                                            new ZerodepDockerHttpClient.Builder()
                                                    .dockerHost(URI.create("unix:///var/run/docker.sock"))
                                                    .build());
    }

    @Provides
    @Singleton
    public ClusterAuthenticationConfig clusterAuth(final AppConfig appConfig) {
        return Objects.requireNonNullElse(appConfig.getClusterAuth(), ClusterAuthenticationConfig.DEFAULT);
    }

    @Provides
    @Singleton
    public LogInfo logInfo(final AppConfig appConfig) {
        return LogInfo.create(appConfig);
    }

    @Provides
    @Singleton
    public ExecutorOptions executorOptions(final AppConfig config) {
        return Objects.requireNonNullElse(config.getOptions(), new ExecutorOptions());
    }

    @Provides
    @Singleton
    public DockerAuthConfig dockerAuthConfig(final AppConfig config) {
        return Objects.requireNonNullElse(config.getDockerAuth(), DockerAuthConfig.DEFAULT);

    }
}
