package com.phonepe.drove.auth.filters;

import com.phonepe.drove.auth.model.ClusterCommHeaders;
import com.phonepe.drove.auth.model.DroveUser;
import io.dropwizard.auth.AuthFilter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.util.Optional;

/**
 *
 */
@Priority(Priorities.AUTHENTICATION)
@Slf4j
public class DroveApplicationInstanceAuthFilter extends AuthFilter<String, DroveUser> {

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        val secret = secretFromHeader(requestContext).orElse(null);
        if(!authenticate(requestContext, secret, SecurityContext.FORM_AUTH)) {
            throw new WebApplicationException(unauthorizedHandler.buildResponse(prefix, realm));
        }
    }

    private Optional<String> secretFromHeader(final ContainerRequestContext requestContext) {
        return Optional.ofNullable(requestContext.getHeaders().getFirst(ClusterCommHeaders.APP_INSTANCE_AUTHORIZATION));
    }

    public static class Builder extends AuthFilterBuilder<String, DroveUser, DroveApplicationInstanceAuthFilter> {

        protected DroveApplicationInstanceAuthFilter newInstance() {
            return new DroveApplicationInstanceAuthFilter();
        }
    }
}
