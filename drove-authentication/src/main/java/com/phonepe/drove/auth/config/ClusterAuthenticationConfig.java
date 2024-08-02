/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.auth.config;

import com.phonepe.drove.models.info.nodedata.NodeType;
import io.dropwizard.validation.ValidationMethod;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;

/**
 *
 */
@Value
@Jacksonized
@Builder
public class ClusterAuthenticationConfig {

    public static final ClusterAuthenticationConfig DEFAULT
            = new ClusterAuthenticationConfig(List.of(
                    new SecretConfig(NodeType.CONTROLLER, "DefaultControllerSecret"),
                    new SecretConfig(NodeType.EXECUTOR, "DefaultExecutorSecret")));

    @Value
    public static class SecretConfig {
        @NotNull
        NodeType nodeType;
        @NotEmpty
        String secret;
    }

    @NotEmpty
    List<SecretConfig> secrets;

    @ValidationMethod(message = "Secrets must be unique")
    boolean isUniqueSecrets() {
        val secretConfigs = Objects.<List<SecretConfig>>requireNonNullElse(secrets, List.of());
        return secretConfigs
                .stream()
                .map(SecretConfig::getSecret)
                .distinct()
                .count() == secretConfigs.size();
    }
}
