package com.phonepe.drove.common.auth.core;

import com.phonepe.drove.common.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.common.auth.model.DroveClusterNode;
import com.phonepe.drove.common.auth.model.DroveUser;
import com.phonepe.drove.common.auth.model.DroveUserRole;
import io.dropwizard.auth.Authenticator;

import java.util.Optional;

/**
 *
 */
public class DroveClusterSecretAuthenticator implements Authenticator<ClusterCredentials, DroveUser> {

    private final ClusterAuthenticationConfig secretAuthenticationConfig;

    public DroveClusterSecretAuthenticator(ClusterAuthenticationConfig secretAuthenticationConfig) {
        this.secretAuthenticationConfig = secretAuthenticationConfig;
    }

    @Override
    public Optional<DroveUser> authenticate(ClusterCredentials clusterCredentials) {
        return secretAuthenticationConfig.getSecrets()
                .stream()
                .filter(secret -> secret.getSecret().equals(clusterCredentials.getSecret()))
                .map(secret -> (DroveUser) new DroveClusterNode(String.format("%s-%s",
                                                                              secret.getNodeType(),
                                                                              clusterCredentials.getNodeId()),
                                                                DroveUserRole.CLUSTER_NODE,
                                                                secret.getNodeType()))
                .findAny();
    }
}
