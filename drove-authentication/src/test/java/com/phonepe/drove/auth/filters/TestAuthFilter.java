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
 * Per-instance state holder so that test classes running in parallel
 * do not share mutable counter / threshold state.
 */
@Slf4j
class TestAuthFilter extends AuthFilter<BasicCredentials, DroveUser> {

    /**
     * Holds the mutable invocation state for a group of {@link TestAuthFilter}
     * instances that belong to the same test class.
     */
    static final class State {
        private volatile int failureThreshold;
        private final AtomicInteger invocationCount = new AtomicInteger();

        void failureThreshold(int count) {
            this.failureThreshold = count;
        }

        void resetCount() {
            invocationCount.set(0);
        }
    }

    private final State state;

    private final DummyAuthFilter upstream = new DummyAuthFilter.Builder()
            .setAuthenticator(new DummyAuthFilter.DummyAuthenticator())
            .setAuthorizer(new DroveAuthorizer())
            .buildAuthFilter();

    TestAuthFilter(State state) {
        this.state = state;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        try {
            if (state.invocationCount.get() >= state.failureThreshold) {
                val message = "Failure " + state.invocationCount.get();
                log.error("Failure message: {}", message);
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED)
                                                          .entity(message)
                                                          .build());
            }
        }
        finally {
            state.invocationCount.incrementAndGet();
        }
        upstream.filter(requestContext);
    }
}
