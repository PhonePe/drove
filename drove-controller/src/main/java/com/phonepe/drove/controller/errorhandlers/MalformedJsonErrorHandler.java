package com.phonepe.drove.controller.errorhandlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.phonepe.drove.controller.utils.ControllerUtils;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Map;

/**
 *
 */
@Provider
public class MalformedJsonErrorHandler implements ExceptionMapper<JsonProcessingException> {
    @Override
    public Response toResponse(JsonProcessingException exception) {
        return ControllerUtils.badRequest(Map.of("validationErrors", exception.getOriginalMessage()),
                                          "JSON validation failure");
    }
}
