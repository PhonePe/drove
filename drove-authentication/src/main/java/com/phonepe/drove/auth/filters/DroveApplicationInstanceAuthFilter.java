/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

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
