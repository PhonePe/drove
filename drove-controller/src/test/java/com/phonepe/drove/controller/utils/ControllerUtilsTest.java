package com.phonepe.drove.controller.utils;

import net.jodah.failsafe.RetryPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 *
 */
class ControllerUtilsTest {
    @Test
    void waitForStateSuccess() {
        assertEquals(StateCheckStatus.MATCH,
                     ControllerUtils.waitForState(
                             () -> StateCheckStatus.MATCH,
                             new RetryPolicy<StateCheckStatus>().withMaxAttempts(1)));
    }

    @Test
    void waitForStateFailure() {
        assertThrows(RuntimeException.class,
                     () -> ControllerUtils.waitForState(
                             () -> {
                                 throw new RuntimeException("Test");
                             },
                             new RetryPolicy<StateCheckStatus>().withMaxAttempts(1)));
    }
}