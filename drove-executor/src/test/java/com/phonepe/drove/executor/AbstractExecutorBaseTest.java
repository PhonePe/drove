package com.phonepe.drove.executor;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.phonepe.drove.common.AbstractTestBase;

import java.net.URI;

/**
 *
 */
public class AbstractExecutorBaseTest extends AbstractTestBase {
    protected static final DockerClient DOCKER_CLIENT
            = DockerClientImpl.getInstance(DefaultDockerClientConfig.createDefaultConfigBuilder().build(),
                                           new ZerodepDockerHttpClient.Builder()
                                                   .dockerHost(URI.create("unix:///var/run/docker.sock"))
                                                   .build());
}
