package com.phonepe.drove.auth.filters;

import com.phonepe.drove.auth.model.DroveUser;
import com.phonepe.drove.auth.model.DroveUserRole;
import io.dropwizard.auth.Auth;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 *
 */
@RolesAllowed({
        DroveUserRole.Values.DROVE_CLUSTER_NODE_ROLE,
        DroveUserRole.Values.DROVE_EXTERNAL_READ_WRITE_ROLE,
        DroveUserRole.Values.DROVE_APPLICATION_INSTANCE_ROLE
})
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class TestResource {

    @Value
    @Jacksonized
    @Builder
    public static class TestResponse {
        String name;
    }

    @GET
    public TestResponse get(@Auth final DroveUser user) {
        return new TestResponse(user.getName());
    }
}
