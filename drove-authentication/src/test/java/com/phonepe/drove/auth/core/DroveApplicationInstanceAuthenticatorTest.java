package com.phonepe.drove.auth.core;

import com.phonepe.drove.auth.model.DroveApplicationInstanceInfo;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class DroveApplicationInstanceAuthenticatorTest {

    @Test
    @SneakyThrows
    void testSuccess() {
        val mgr = mock(ApplicationInstanceTokenManager.class);
        when(mgr.verify(anyString()))
                .thenReturn(Optional.of(new DroveApplicationInstanceInfo("app1",
                                                                         "instance1",
                                                                         "ex1")));

        val auth = new DroveApplicationInstanceAuthenticator(mgr);
        assertTrue(auth.authenticate("Test").isPresent());
    }

    @Test
    @SneakyThrows
    void testFailure() {
        val mgr = mock(ApplicationInstanceTokenManager.class);
        when(mgr.verify(anyString())).thenReturn(Optional.empty());

        val auth = new DroveApplicationInstanceAuthenticator(mgr);
        assertTrue(auth.authenticate("Test").isEmpty());
    }

}