package com.phonepe.drove.auth.filters;

import io.dropwizard.auth.AuthFilter;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.security.Principal;
import java.util.List;

/**
 *
 */
@Priority(Priorities.AUTHENTICATION)
public class CompositeAuthFilter <C, P extends Principal> extends AuthFilter<C, P> {
    @SuppressWarnings("rawtypes")
    private final List<AuthFilter> handlers;
    private final boolean throwLastException;

    public CompositeAuthFilter(@SuppressWarnings("rawtypes") List<AuthFilter> handlers, boolean throwLastException) {
        this.handlers = handlers;
        this.throwLastException = throwLastException;
    }

    @Override
    public void filter(ContainerRequestContext containerRequestContext) throws IOException {
        WebApplicationException firstException = null;
        WebApplicationException lastException = null;
        for (@SuppressWarnings("rawtypes") AuthFilter authFilter : handlers) {
            final SecurityContext securityContext = containerRequestContext.getSecurityContext();
            try {
                authFilter.filter(containerRequestContext);
                if (securityContext != containerRequestContext.getSecurityContext()) {
                    return;
                }
            } catch (WebApplicationException e) {
                if (firstException == null) {
                    firstException = e;
                }
                lastException = e;
            }
        }

        if (firstException == null) {
            throw unauthorizedHandler.buildException(prefix, realm);
        }
        throw throwLastException ? lastException : firstException;
    }
}
