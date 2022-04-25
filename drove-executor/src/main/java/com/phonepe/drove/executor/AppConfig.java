package com.phonepe.drove.executor;

import com.phonepe.drove.common.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.common.zookeeper.ZkConfig;
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

    @NotNull
    @Valid
    private ResourceConfig resources = new ResourceConfig();

    @Valid
    private ClusterAuthenticationConfig clusterAuth;

    @Valid
    private ExecutorOptions options;
}
