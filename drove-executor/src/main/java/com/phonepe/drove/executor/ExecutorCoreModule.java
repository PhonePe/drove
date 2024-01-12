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
import com.google.inject.name.Names;
import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.common.discovery.ZkNodeDataStore;
import com.phonepe.drove.common.model.ControllerMessageType;
import com.phonepe.drove.common.model.controller.ControllerMessage;
import com.phonepe.drove.common.net.MessageSender;
import com.phonepe.drove.common.zookeeper.ZkConfig;
import com.phonepe.drove.executor.dockerauth.DockerAuthConfig;
import com.phonepe.drove.executor.engine.ApplicationInstanceEngine;
import com.phonepe.drove.executor.engine.RemoteControllerMessageSender;
import com.phonepe.drove.executor.engine.TaskInstanceEngine;
import com.phonepe.drove.executor.logging.LogInfo;
import com.phonepe.drove.executor.managed.ExecutorIdManager;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.resourcemgmt.resourceloaders.NumaActivationResourceLoader;
import com.phonepe.drove.executor.resourcemgmt.resourceloaders.NumaCtlBasedResourceLoader;
import com.phonepe.drove.executor.resourcemgmt.resourceloaders.OverProvisioningResourceLoader;
import com.phonepe.drove.executor.resourcemgmt.resourceloaders.ResourceLoader;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.Duration;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

import javax.inject.Singleton;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.SynchronousQueue;

/**
 *
 */
public class ExecutorCoreModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(NodeDataStore.class).to(ZkNodeDataStore.class);
        bind(new TypeLiteral<MessageSender<ControllerMessageType, ControllerMessage>>() {
        })
                .to(RemoteControllerMessageSender.class);
        bind(ResourceLoader.class).annotatedWith(Names.named(ResourceLoaderIdentifiers.NUMA_CTL_BASED_RESOURCE_LOADER))
                .to(NumaCtlBasedResourceLoader.class);
        bind(ResourceLoader.class).annotatedWith(Names.named(ResourceLoaderIdentifiers.NUMA_ACTIVATION_RESOURCE_LOADER))
                .to(NumaActivationResourceLoader.class);
        bind(ResourceLoader.class).to(OverProvisioningResourceLoader.class);
    }


    @Provides
    @Singleton
    public ApplicationInstanceEngine appEngine(
            final Environment environment,
            final Injector injector,
            final ResourceManager resourceDB,
            final ExecutorIdManager executorIdManager,
            final DockerClient client) {
        val executorService = environment.lifecycle()
                .executorService("app-engine")
                .maxThreads(Integer.MAX_VALUE)
                .minThreads(0)
                .workQueue(new SynchronousQueue<>())
                .keepAliveTime(Duration.seconds(60))

                .build();
        return new ApplicationInstanceEngine(
                executorIdManager,
                executorService,
                new InjectingApplicationInstanceActionFactory(injector),
                resourceDB,
                client);
    }

    @Provides
    @Singleton
    public TaskInstanceEngine taskEngine(
            final Environment environment,
            final Injector injector,
            final ResourceManager resourceDB,
            final ExecutorIdManager executorIdManager,
            final DockerClient client) {
        val executorService = environment.lifecycle()
                .executorService("task-engine")
                .maxThreads(Integer.MAX_VALUE)
                .minThreads(0)
                .workQueue(new SynchronousQueue<>())
                .keepAliveTime(Duration.seconds(60))
                .build();
        return new TaskInstanceEngine(
                executorIdManager,
                executorService,
                new InjectingTaskActionFactory(injector),
                resourceDB,
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
        return Objects.requireNonNullElse(appConfig.getResources(), ResourceConfig.DEFAULT);
    }

    @Provides
    @Singleton
    public MetricRegistry registry(final Environment environment) {
        return environment.metrics();
    }

    @Provides
    @Singleton
    public DockerClient dockerClient(final ExecutorOptions executorOptions) {
        val timeout = java.time.Duration.ofMillis(
                Objects.requireNonNullElse(executorOptions.getContainerCommandTimeout(),
                                           ExecutorOptions.DEFAULT_CONTAINER_COMMAND_TIMEOUT)
                        .toMilliseconds());
        return DockerClientImpl.getInstance(
                DefaultDockerClientConfig.createDefaultConfigBuilder()
                        .build(),
                new ZerodepDockerHttpClient.Builder()
                        .dockerHost(URI.create("unix:///var/run/docker.sock"))
                        .responseTimeout(timeout)
                        .connectionTimeout(java.time.Duration.ofSeconds(1))
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
        return Objects.requireNonNullElse(config.getOptions(), ExecutorOptions.DEFAULT);
    }

    @Provides
    @Singleton
    public DockerAuthConfig dockerAuthConfig(final AppConfig config) {
        return Objects.requireNonNullElse(config.getDockerAuth(), DockerAuthConfig.DEFAULT);

    }

    @Provides
    @Singleton
    public CloseableHttpClient httpClient() {
        return CommonUtils.createHttpClient();
    }

    public static final class ResourceLoaderIdentifiers {
        public static final String NUMA_CTL_BASED_RESOURCE_LOADER = "NumaCtlBasedResourceLoader";
        public static final String NUMA_ACTIVATION_RESOURCE_LOADER = "NumaActivationResourceLoader";

    }

}
