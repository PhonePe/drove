package com.phonepe.drove.common.auth.config;

import com.phonepe.drove.models.info.nodedata.NodeType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
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
