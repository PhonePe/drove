package com.phonepe.drove.controller.errorhandlers;

import lombok.val;
import org.junit.jupiter.api.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class WebAppErrorHandlerTest {
    @Test
    void testError() {
        try (val response = new WebAppErrorHandler()
                .toResponse(new WebApplicationException(Response.serverError().build()))) {
            assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                         response.getStatus());
        }
    }
}