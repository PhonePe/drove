package com.phonepe.drove.controller.errorhandlers;

import com.phonepe.drove.controller.utils.ControllerUtils;
import io.dropwizard.jersey.validation.ConstraintMessage;
import io.dropwizard.jersey.validation.JerseyViolationException;
import lombok.val;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Map;

/**
 * Handles param validations from jersey and returns standard error
 */
@Provider
public class ValidationErrorHandler implements ExceptionMapper<JerseyViolationException> {
    @Override
    public Response toResponse(JerseyViolationException exception) {
        val invocable = exception.getInvocable();
        return ControllerUtils.badRequest(Map.of("validationErrors", exception.getConstraintViolations()
                                                  .stream()
                                                  .map(violation -> ConstraintMessage.getMessage(violation, invocable))
                                                  .toList()), "Command validation failure");
    }
}
