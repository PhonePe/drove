package com.phonepe.drove.auth.config;

import com.phonepe.drove.models.info.nodedata.NodeType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.phonepe.drove.auth.config.ClusterAuthenticationConfig.SecretConfig;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
class ClusterAuthenticationConfigTest {

    @Test
    void test() {
        assertTrue(new ClusterAuthenticationConfig(List.of(new SecretConfig(NodeType.CONTROLLER, "CS"),
                                                           new SecretConfig(NodeType.EXECUTOR, "ES")))
                           .isUniqueSecrets());
        assertFalse(new ClusterAuthenticationConfig(List.of(new SecretConfig(NodeType.CONTROLLER, "SS"),
                                                           new SecretConfig(NodeType.EXECUTOR, "SS")))
                           .isUniqueSecrets());
    }

}