package com.phonepe.drove.controller.errorhandlers;

import lombok.val;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
class CatchAllErrorHandlerTest {
    @Test
    void testError() {
        try (val response = new CatchAllErrorHandler()
                .toResponse(new IllegalStateException("Test error"))) {
            assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                         response.getStatus());
        }
    }
}