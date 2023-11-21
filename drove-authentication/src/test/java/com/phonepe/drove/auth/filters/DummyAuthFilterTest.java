package com.phonepe.drove.auth.filters;

import com.phonepe.drove.auth.core.DroveAuthorizer;
import com.phonepe.drove.auth.model.DroveUser;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.jersey.jackson.JacksonFeature;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import lombok.val;
import org.eclipse.jetty.http.HttpStatus;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.client.ClientBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@ExtendWith(DropwizardExtensionsSupport.class)
class DummyAuthFilterTest extends AbstractAuthTestBase {
    private static final ResourceExtension EXT = ResourceExtension.builder()
            .setTestContainerFactory(new GrizzlyWebTestContainerFactory())
            .addResource(new TestResource())
            .addProvider(new AuthDynamicFeature(
                    new DummyAuthFilter.Builder()
                            .setAuthenticator(new DummyAuthFilter.DummyAuthenticator())
                            .setAuthorizer(new DroveAuthorizer())
                            .buildAuthFilter()))
            .addProvider(RolesAllowedDynamicFeature.class)
            .addProvider(new AuthValueFactoryProvider.Binder<>(DroveUser.class))
            .build();

    @Test
    void testSuccess() {
        val client = ClientBuilder.newBuilder()
                .register(new JacksonFeature(MAPPER))
                .build();
        try (val r = client.target(EXT.target("/").getUri()).request().get()) {
            assertEquals(HttpStatus.OK_200, r.getStatus());
            assertEquals("default-user", r.readEntity(TestResource.TestResponse.class).getName());
        }
    }
}