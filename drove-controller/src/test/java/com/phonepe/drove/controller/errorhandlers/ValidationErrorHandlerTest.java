package com.phonepe.drove.controller.errorhandlers;

import io.dropwizard.jersey.validation.JerseyViolationException;
import lombok.val;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValidationErrorHandlerTest {

    @Test
    void testError() {
        try (val response = new ValidationErrorHandler()
                .toResponse(new JerseyViolationException(Set.of(), null))) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(),
                         response.getStatus());
        }
    }

}