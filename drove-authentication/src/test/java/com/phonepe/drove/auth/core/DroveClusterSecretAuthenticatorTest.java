package com.phonepe.drove.auth.core;

import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.auth.model.DroveClusterNode;
import com.phonepe.drove.models.info.nodedata.NodeType;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class DroveClusterSecretAuthenticatorTest {

    @Test
    void test() {

        val authenticator = new DroveClusterSecretAuthenticator(ClusterAuthenticationConfig.DEFAULT);
        assertEquals(NodeType.CONTROLLER,
                     authenticator.authenticate(new ClusterCredentials("c1", "DefaultControllerSecret"))
                             .map(u -> ((DroveClusterNode) u).getNodeType())
                             .orElse(null));
        assertEquals(NodeType.EXECUTOR,
                     authenticator.authenticate(new ClusterCredentials("e1", "DefaultExecutorSecret"))
                             .map(u -> ((DroveClusterNode) u).getNodeType())
                             .orElse(null));
        assertTrue(authenticator.authenticate(new ClusterCredentials("e2", "WrongExecutorSecret")).isEmpty());
    }

}