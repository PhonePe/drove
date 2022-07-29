package com.phonepe.drove.controller.errorhandlers;

import com.phonepe.drove.models.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Map;

/**
 *
 */
@Provider
@Slf4j
public class WebAppExceptionHandler implements ExceptionMapper<WebApplicationException> {

    @Override
    public Response toResponse(WebApplicationException exception) {
        return Response.status(exception.getResponse().getStatus())
                .entity(ApiResponse.failure(Map.of("error", exception.getMessage()), "Application error"))
                .build();
    }
}
