package com.phonepe.drove.executor;

import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.common.zookeeper.ZkConfig;
import com.phonepe.drove.executor.dockerauth.DockerAuthConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import io.dropwizard.Configuration;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AppConfig extends Configuration {
    @NotNull
    @Valid
    private ZkConfig zookeeper;

    @Valid
    private ResourceConfig resources;

    @Valid
    private ClusterAuthenticationConfig clusterAuth;

    @Valid
    private ExecutorOptions options;

    @Valid
    private DockerAuthConfig dockerAuth;
}
