package com.phonepe.drove.controller;

import com.phonepe.drove.common.auth.BasicAuthConfig;
import com.phonepe.drove.common.auth.ClusterAuthenticationConfig;
import com.phonepe.drove.common.zookeeper.ZkConfig;
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
    private ClusterAuthenticationConfig clusterAuth;

    @Valid
    BasicAuthConfig userAuth;
}
