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
import com.phonepe.drove.auth.core.DroveAuthorizer;
import com.phonepe.drove.auth.core.DroveExternalAuthenticator;
import com.phonepe.drove.auth.model.DroveExternalUserInfo;
import com.phonepe.drove.auth.model.DroveUser;
import com.phonepe.drove.auth.model.DroveUserRole;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.auth.basic.BasicCredentialAuthFilter;
import io.dropwizard.jersey.jackson.JacksonFeature;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import lombok.val;
import org.eclipse.jetty.http.HttpStatus;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.client.ClientBuilder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@ExtendWith(DropwizardExtensionsSupport.class)
class DroveBasicAuthTest extends AbstractAuthTestBase {

    private static final ResourceExtension EXT = ResourceExtension.builder()
            .setTestContainerFactory(new GrizzlyWebTestContainerFactory())
            .addResource(new TestResource())
            .addProvider(new AuthDynamicFeature(
                    new BasicCredentialAuthFilter.Builder<DroveUser>()
                            .setAuthenticator(new DroveExternalAuthenticator(
                                    new BasicAuthConfig(
                                            true,
                                            List.of(new DroveExternalUserInfo("test-user",
                                                                              "test-password",
                                                                              DroveUserRole.EXTERNAL_ROOT)),
                                            BasicAuthConfig.AuthEncoding.PLAIN,
                                            "")))
                            .setAuthorizer(new DroveAuthorizer())
                            .setPrefix("Basic")
                            .buildAuthFilter()))
                    .addProvider(RolesAllowedDynamicFeature.class)
                    .addProvider(new AuthValueFactoryProvider.Binder<>(DroveUser.class))
                    .build();


    @Test
    void testSuccess() {
        val client = ClientBuilder.newBuilder()
                .register(HttpAuthenticationFeature.basicBuilder()
                                  .nonPreemptive()
                                  .credentials("test-user", "test-password")
                                  .build())
                .register(new JacksonFeature(MAPPER))
                .build();
        try (val r = client.target(EXT.target("/").getUri()).request().get()) {
            assertEquals(HttpStatus.OK_200, r.getStatus());
            assertEquals("test-user", r.readEntity(TestResource.TestResponse.class).getName());
        }
    }

    @Test
    void testFailureNoAuth() {
        val client = ClientBuilder.newBuilder()
                .register(new JacksonFeature(MAPPER))
                .build();
        try (val r = client.target(EXT.target("/").getUri()).request().get()) {
            assertEquals(HttpStatus.UNAUTHORIZED_401, r.getStatus());
        }
    }

    @Test
    void testFailureWrongCred() {
        val client = ClientBuilder.newBuilder()
                .register(HttpAuthenticationFeature.basicBuilder()
                                  .nonPreemptive()
                                  .credentials("test-user", "wrong-password")
                                  .build())
                .register(new JacksonFeature(MAPPER))
                .build();
        try (val r = client.target(EXT.target("/").getUri()).request().get()) {
            assertEquals(HttpStatus.UNAUTHORIZED_401, r.getStatus());
        }
    }

}