package com.phonepe.drove.auth.filters;

import com.phonepe.drove.auth.core.DroveAuthorizer;
import com.phonepe.drove.auth.model.DroveUser;
import io.dropwizard.auth.AuthFilter;
import io.dropwizard.auth.basic.BasicCredentials;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 */
@Slf4j
class TestAuthFilter extends AuthFilter<BasicCredentials, DroveUser> {
    private static int failureThreshold;
    private static final AtomicInteger INVOCATION_COUNT = new AtomicInteger();

    private final DummyAuthFilter upstream = new DummyAuthFilter.Builder()
            .setAuthenticator(new DummyAuthFilter.DummyAuthenticator())
            .setAuthorizer(new DroveAuthorizer())
            .buildAuthFilter();

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        try {
            if (INVOCATION_COUNT.get() >= failureThreshold) {
                val message = "Failure " + INVOCATION_COUNT.get();
                log.error("Failure message: {}", message);
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED)
                                                          .entity(message)
                                                          .build());
            }
        }
        finally {
            INVOCATION_COUNT.incrementAndGet();
        }
        upstream.filter(requestContext);
    }

    public static void failureThreshold(int count) {
        failureThreshold = count;
    }

    public static void resetCount() {
        INVOCATION_COUNT.set(0);
    }
}
