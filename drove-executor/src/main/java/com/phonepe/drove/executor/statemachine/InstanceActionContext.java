package com.phonepe.drove.executor.statemachine;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.phonepe.drove.common.ActionContext;
import com.phonepe.drove.common.model.InstanceSpec;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.net.URI;
import java.util.concurrent.Future;

/**
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class InstanceActionContext extends ActionContext {
    private final DockerClient client;
    private final InstanceSpec instanceSpec;
    private String dockerImageId;
    private String dockerInstanceId;
    private Future<?> loggerFuture;

    public InstanceActionContext(InstanceSpec instanceSpec) {
        super();
        this.instanceSpec = instanceSpec;
        this.client = DockerClientImpl.getInstance(DefaultDockerClientConfig.createDefaultConfigBuilder()
                                                      .build(),
                                              new ZerodepDockerHttpClient.Builder()
                                                      .dockerHost(URI.create("unix:///var/run/docker.sock"))
                                                      .build());
    }
}
