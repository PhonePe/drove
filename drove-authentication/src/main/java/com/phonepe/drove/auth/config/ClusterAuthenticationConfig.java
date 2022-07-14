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
