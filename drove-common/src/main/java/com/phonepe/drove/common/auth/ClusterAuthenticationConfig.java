package com.phonepe.drove.common.auth;

import com.phonepe.drove.models.info.nodedata.NodeType;
import lombok.Value;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 *
 */
@Value
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
}
