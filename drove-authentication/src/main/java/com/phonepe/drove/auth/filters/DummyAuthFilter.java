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
