package com.phonepe.drove.auth.core;

import lombok.val;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
class DroveUnauthorizedHandlerTest {

    @Test
    void test() {
        val h = new DroveUnauthorizedHandler();
        try(val r = h.buildResponse("test", "test")) {
            assertEquals(HttpStatus.UNAUTHORIZED_401, r.getStatus());
        }
    }
}