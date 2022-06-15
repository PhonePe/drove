package com.phonepe.drove.auth.core;

import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.auth.model.DroveUser;
import com.phonepe.drove.auth.model.DroveUserRole;
import com.phonepe.drove.auth.model.DroveClusterNode;
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
