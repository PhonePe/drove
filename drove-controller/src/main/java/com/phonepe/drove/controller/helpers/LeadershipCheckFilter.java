package com.phonepe.drove.controller.helpers;

import com.phonepe.drove.common.discovery.leadership.LeadershipObserver;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.models.api.ApiErrorCode;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

/**
 *
 */
@Provider
@Singleton
public class LeadershipCheckFilter implements ContainerRequestFilter {
    private final LeadershipEnsurer leadershipEnsurer;
    private final LeadershipObserver leadershipObserver;

    @Inject
    public LeadershipCheckFilter(
            LeadershipEnsurer leadershipEnsurer,
            LeadershipObserver leadershipObserver) {
        this.leadershipEnsurer = leadershipEnsurer;
        this.leadershipObserver = leadershipObserver;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (!leadershipEnsurer.isLeader()) {
            val leader = leadershipObserver.leader().orElse(null);
            val uriInfo = requestContext.getUriInfo();
            final var absolutePath = uriInfo.getAbsolutePath().getPath();
            if (null == leader) {
                requestContext.abortWith(Response.status(Response.Status.BAD_REQUEST)
                                                 .entity(new ApiResponse<Void>(
                                                         ApiErrorCode.FAILED,
                                                         null,
                                                         "This node is not the leader controller"))
                                                 .build());
            }
            else {

                val uri = UriBuilder.fromPath(absolutePath.contains("/ui/")
                                              ? absolutePath.replace("/apis/ui", "")
                                              : "")
                        .host(leader.getHostname())
                        .port(leader.getPort())
                        .scheme(leader.getTransportType() == NodeTransportType.HTTP
                                ? "http"
                                : "https")
                        .build();
                requestContext.abortWith(Response.seeOther(uri).build());
            }
        }
    }
}
