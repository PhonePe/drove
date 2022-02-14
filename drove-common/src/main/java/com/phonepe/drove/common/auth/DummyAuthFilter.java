package com.phonepe.drove.common.auth;

import io.dropwizard.auth.AuthFilter;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;
import lombok.val;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.util.Optional;

/**
 *
 */
public class DummyAuthFilter extends AuthFilter<BasicCredentials, DroveUser> {
    public static final class DummyAuthenticator implements Authenticator<BasicCredentials, DroveUser> {

        @Override
        public Optional<DroveUser> authenticate(BasicCredentials credentials) throws AuthenticationException {
            val dummyUser = BasicAuthConfig.DEFAULT.getUsers().get(0);
            return Optional.of(new DroveExternalUser(dummyUser.getUsername(), dummyUser.getRole(), null));
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if(!authenticate(requestContext, new BasicCredentials("", ""), SecurityContext.BASIC_AUTH)) {
            throw new WebApplicationException(unauthorizedHandler.buildResponse(prefix, realm));
        }
    }

    public static class Builder extends
            AuthFilterBuilder<BasicCredentials, DroveUser, DummyAuthFilter> {

        @Override
        protected DummyAuthFilter newInstance() {
            return new DummyAuthFilter();
        }
    }
}
