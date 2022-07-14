package com.phonepe.drove.auth.filters;

import com.phonepe.drove.auth.config.BasicAuthConfig;
import com.phonepe.drove.auth.model.DroveExternalUser;
import com.phonepe.drove.auth.model.DroveUser;
import io.dropwizard.auth.AuthFilter;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;
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
public class DummyAuthFilter extends AuthFilter<BasicCredentials, DroveUser> {
    public static final class DummyAuthenticator implements Authenticator<BasicCredentials, DroveUser> {

        @Override
        public Optional<DroveUser> authenticate(BasicCredentials credentials) {
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
