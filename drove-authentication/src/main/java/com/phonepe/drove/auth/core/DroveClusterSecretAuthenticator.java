/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.auth.core;

import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.auth.model.DroveUser;
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
                                                                secret.getNodeType()))
                .findAny();
    }
}
