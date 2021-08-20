package com.phonepe.drove.executor;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.phonepe.drove.common.ActionContext;
import com.phonepe.drove.internalmodels.InstanceSpec;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.net.URI;

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

    public InstanceActionContext(InstanceSpec instanceSpec) {
        this.instanceSpec = instanceSpec;
        client = DockerClientImpl.getInstance(DefaultDockerClientConfig.createDefaultConfigBuilder()
                                                      .build(),
                                              new ZerodepDockerHttpClient.Builder()
                                                      .dockerHost(URI.create("unix:///var/run/docker.sock"))
                                                      .build());
    }
}
