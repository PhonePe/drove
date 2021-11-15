package com.phonepe.drove.controller.helpers;

import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.models.api.ApiErrorCode;
import com.phonepe.drove.models.api.ApiResponse;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

/**
 *
 */
@Provider
@Singleton
public class LeadershipCheckFilter implements ContainerRequestFilter {
    private final LeadershipEnsurer leadershipEnsurer;

    @Inject
    public LeadershipCheckFilter(LeadershipEnsurer leadershipEnsurer) {
        this.leadershipEnsurer = leadershipEnsurer;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (!leadershipEnsurer.isLeader()) {
            requestContext.abortWith(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity(new ApiResponse<Void>(
                                    ApiErrorCode.FAILED, null, "This node is not the leader controller"))
                            .build());
        }
    }
}
