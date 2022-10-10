package com.phonepe.drove.controller;

import com.phonepe.drove.auth.config.ApplicationAuthConfig;
import com.phonepe.drove.auth.config.BasicAuthConfig;
import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.common.zookeeper.ZkConfig;
import com.phonepe.drove.controller.config.ControllerOptions;
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
    private ControllerOptions options;

    @Valid
    private ClusterAuthenticationConfig clusterAuth;

    @Valid
    private ApplicationAuthConfig instanceAuth;

    @Valid
    private BasicAuthConfig userAuth;
}
