package com.phonepe.drove.controller.errorhandlers;

import com.fasterxml.jackson.core.JsonParseException;
import lombok.val;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
class MalformedJsonErrorHandlerTest {
    @Test
    void testError() {
        try (val response = new MalformedJsonErrorHandler()
                .toResponse(new JsonParseException(null, "Test error"))) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(),
                         response.getStatus());
        }
    }
}