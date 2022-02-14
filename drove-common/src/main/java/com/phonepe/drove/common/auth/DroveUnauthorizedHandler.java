package com.phonepe.drove.common.auth;

import io.dropwizard.auth.UnauthorizedHandler;

import javax.ws.rs.core.Response;

/**
 *
 */
public class DroveUnauthorizedHandler implements UnauthorizedHandler {
    @Override
    public Response buildResponse(String prefix, String realm) {
        return Response.status(Response.Status.UNAUTHORIZED).build();
    }
}
