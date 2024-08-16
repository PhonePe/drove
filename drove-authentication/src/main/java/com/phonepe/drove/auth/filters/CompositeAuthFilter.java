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

import io.dropwizard.auth.AuthFilter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Set;

/**
 *
 */
@Priority(Priorities.AUTHENTICATION)
@Slf4j
public class CompositeAuthFilter <C, P extends Principal> extends AuthFilter<C, P> {

    @SuppressWarnings("rawtypes")
    private final List<AuthFilter> handlers;
    private final boolean throwLastException;
    private final Set<String> auditedMethods;

    public CompositeAuthFilter(@SuppressWarnings("rawtypes") List<AuthFilter> handlers, boolean throwLastException, Set<String> auditedMethods) {
        this.handlers = handlers;
        this.throwLastException = throwLastException;
        this.auditedMethods = auditedMethods;
    }

    @Override
    public void filter(ContainerRequestContext containerRequestContext) throws IOException {
        WebApplicationException firstException = null;
        WebApplicationException lastException = null;
        for (@SuppressWarnings("rawtypes") AuthFilter authFilter : handlers) {
            final SecurityContext securityContext = containerRequestContext.getSecurityContext();
            try {
                authFilter.filter(containerRequestContext);
                val updated = containerRequestContext.getSecurityContext();
                if (securityContext != updated) {
                    val method = containerRequestContext.getMethod();
                    if (auditedMethods.contains(method)) {
                        log.info("ACCESS_AUDIT: Api [{}] {}{} called by {}",
                                 method,
                                 containerRequestContext.getUriInfo().getBaseUri().getPath(),
                                 containerRequestContext.getUriInfo().getPath(true),
                                 updated.getUserPrincipal().getName());
                    }
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
