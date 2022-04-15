package com.phonepe.drove.controller.errorhandlers;

import com.phonepe.drove.models.api.ApiResponse;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Map;

/**
 *
 */
@Provider
public class CatchAllErrorHandler implements ExceptionMapper<Exception> {
    @Override
    public Response toResponse(Exception exception) {
        return Response.serverError().entity(ApiResponse.failure(Map.of("failure", exception.getMessage()),
                                                                 "Unexpected Server error")).build();
    }
}
