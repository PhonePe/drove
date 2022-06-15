package com.phonepe.drove.auth.filters;

import com.phonepe.drove.auth.core.ClusterCredentials;
import com.phonepe.drove.auth.model.DroveUser;
import io.dropwizard.auth.AuthFilter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.util.Optional;

import static com.phonepe.drove.auth.core.AuthConstansts.NODE_ID_HEADER;

/**
 *
 */
@Priority(Priorities.AUTHENTICATION)
@Slf4j
public class DroveClusterAuthFilter extends AuthFilter<ClusterCredentials, DroveUser> {

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        val nodeId = requestContext.getHeaders().getFirst(NODE_ID_HEADER);
        val secret = secretFromHeader(requestContext).orElse(null);
        if(!authenticate(requestContext, new ClusterCredentials(nodeId, secret), SecurityContext.DIGEST_AUTH)) {
            throw new WebApplicationException(unauthorizedHandler.buildResponse(prefix, realm));
        }
    }

    private Optional<String> secretFromHeader(final ContainerRequestContext requestContext) {
        val header = requestContext.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null) {
            int space = header.indexOf(' ');
            if (space > 0) {
                final String method = header.substring(0, space);
                if ("Bearer".equalsIgnoreCase(method)) {
                    final String rawToken = header.substring(space + 1);
                    return Optional.of(rawToken);
                }
            }
        }
        return Optional.empty();
    }

    public static class Builder extends AuthFilter.AuthFilterBuilder<ClusterCredentials, DroveUser, DroveClusterAuthFilter> {

        protected DroveClusterAuthFilter newInstance() {
            return new DroveClusterAuthFilter();
        }
    }
}
