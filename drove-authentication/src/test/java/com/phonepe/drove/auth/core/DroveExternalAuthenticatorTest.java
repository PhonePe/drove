package com.phonepe.drove.auth.core;

import com.phonepe.drove.auth.config.BasicAuthConfig;
import com.phonepe.drove.auth.model.DroveExternalUser;
import com.phonepe.drove.auth.model.DroveExternalUserInfo;
import com.phonepe.drove.auth.model.DroveUserRole;
import io.dropwizard.auth.basic.BasicCredentials;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
class DroveExternalAuthenticatorTest {

    @Test
    void test() {
        val authenticator = new DroveExternalAuthenticator(new BasicAuthConfig(true,
                                                                               List.of(new DroveExternalUserInfo(
                                                                                       "test-user",
                                                                                       "test-password",
                                                                                       DroveUserRole.EXTERNAL_READ_WRITE)),
                                                                               BasicAuthConfig.AuthEncoding.PLAIN,
                                                                               null));
        assertEquals("test-user", authenticator.authenticate(new BasicCredentials("test-user", "test-password"))
                .map(u -> ((DroveExternalUser) u).getName())
                .orElse(null));
        assertTrue(authenticator.authenticate(new BasicCredentials("wrong-user", "test-password")).isEmpty());
        assertTrue(authenticator.authenticate(new BasicCredentials("test-user", "wrong-password")).isEmpty());
    }

    @Test
    void testDisabled() {
        val authenticator = new DroveExternalAuthenticator(BasicAuthConfig.DEFAULT);
        assertEquals("default-user", authenticator.authenticate(new BasicCredentials("wrong-user", "wrong-password"))
                .map(u -> ((DroveExternalUser) u).getName())
                .orElse(null));

    }
}