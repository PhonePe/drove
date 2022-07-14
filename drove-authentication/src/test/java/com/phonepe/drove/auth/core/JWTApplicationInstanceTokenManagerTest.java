package com.phonepe.drove.auth.core;

import com.phonepe.drove.auth.config.ApplicationAuthConfig;
import com.phonepe.drove.auth.model.DroveApplicationInstanceInfo;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class JWTApplicationInstanceTokenManagerTest {

    @Test
    void test() {
        val mgr = new JWTApplicationInstanceTokenManager(new ApplicationAuthConfig("test-secret"));
        val info = new DroveApplicationInstanceInfo("test_app", "inst1", "exec1");
        val token = mgr.generate(info).orElse(null);
        assertNotNull(token);
        val retrieved = mgr.verify(token).orElse(null);
        assertNotNull(retrieved);
        assertEquals(info, retrieved);

        assertNull(mgr.verify("WrongToken").orElse(null));
    }

}