package com.phonepe.drove.auth.model;

import com.phonepe.drove.models.info.nodedata.NodeType;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 *
 */
class DroveUserVisitorAdaptorTest {

    @Test
    void test() {
        val visitor = new DroveUserVisitorAdaptor<>(false) {};
        assertFalse(new DroveClusterNode("test", NodeType.EXECUTOR).accept(visitor));
        assertFalse(new DroveApplicationInstance("test", null).accept(visitor));
        assertFalse(new DroveExternalUser("test", DroveUserRole.EXTERNAL_READ_WRITE, null).accept(visitor));
    }

}