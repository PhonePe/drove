/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.controller.helpers;

import com.phonepe.drove.common.discovery.leadership.LeadershipObserver;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.models.api.ApiErrorCode;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import lombok.val;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

/**
 * Blocks request to non leader nodes. Redirects to leader node for UI requests.
 */
@Provider
@Singleton
@Priority(Priorities.USER)
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
            val absolutePath = uriInfo.getAbsolutePath().getPath();
            if (null == leader) {
                fail(requestContext);
            }
            else {
                if(absolutePath.contains("/ui/")) {
                    val uri = UriBuilder.fromPath(absolutePath.replace("/apis/ui", ""))
                            .host(leader.getHostname())
                            .port(leader.getPort())
                            .scheme(leader.getTransportType() == NodeTransportType.HTTP
                                    ? "http"
                                    : "https")
                            .build();
                    requestContext.abortWith(Response.seeOther(uri).build());
                }
                else {
                    fail(requestContext);
                }
            }
        }
    }

    private void fail(ContainerRequestContext requestContext) {
        requestContext.abortWith(Response.status(Response.Status.BAD_REQUEST)
                                         .entity(new ApiResponse<Void>(
                                                 ApiErrorCode.FAILED,
                                                 null,
                                                 "This node is not the leader controller"))
                                         .build());
    }
}
