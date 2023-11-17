package com.phonepe.drove.auth.core;

import com.phonepe.drove.auth.model.DroveClusterNode;
import com.phonepe.drove.auth.model.DroveUserRole;
import com.phonepe.drove.models.info.nodedata.NodeType;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
class DroveAuthorizerTest {

    @Test
    void test() {
        val da = new DroveAuthorizer(false);
        assertFalse(da.authorize(new DroveClusterNode("Test", NodeType.CONTROLLER),
                     DroveUserRole.Values.DROVE_EXTERNAL_READ_ONLY_ROLE));
        assertTrue(da.authorize(new DroveClusterNode("Test", NodeType.CONTROLLER),
                     DroveUserRole.Values.DROVE_CLUSTER_NODE_ROLE));
    }

    @Test
    void testDisableReadAuth() {
        val da = new DroveAuthorizer(true);
        assertTrue(da.authorize(new DroveClusterNode("Test", NodeType.CONTROLLER),
                     DroveUserRole.Values.DROVE_EXTERNAL_READ_ONLY_ROLE));
        assertTrue(da.authorize(new DroveClusterNode("Test", NodeType.CONTROLLER),
                     DroveUserRole.Values.DROVE_CLUSTER_NODE_ROLE));
    }

}