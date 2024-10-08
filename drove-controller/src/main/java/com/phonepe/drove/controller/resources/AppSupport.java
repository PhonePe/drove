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

package com.phonepe.drove.controller.resources;

import com.codahale.metrics.annotation.Timed;
import com.phonepe.drove.auth.model.*;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import io.dropwizard.auth.Auth;
import lombok.val;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.*;
import java.util.stream.Collectors;

import static com.phonepe.drove.models.instance.InstanceState.*;

/**
 *
 */
@Path("/v1/internal")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed(DroveUserRole.Values.DROVE_APPLICATION_INSTANCE_ROLE)
@Timed
public class AppSupport {

    private final ApplicationStateDB appDB;
    private final ApplicationInstanceInfoDB instanceInfoDB;

    @Inject
    public AppSupport(ApplicationStateDB appDB, ApplicationInstanceInfoDB instanceInfoDB) {
        this.appDB = appDB;
        this.instanceInfoDB = instanceInfoDB;
    }

    @GET
    @Path("/instances")
    public ApiResponse<List<InstanceInfo>> siblingInstances(
            @Auth final DroveUser droveUser,
            @QueryParam("state") final Set<InstanceState> requiredStates,
            @QueryParam("forApp") boolean forApp) {
        val info = extractInstanceInfo(droveUser);
        if (info == null) {
            return ApiResponse.failure(
                    "This api is applicable for calls by app instances from inside the cluster only");
        }
        if (!hasAccess(info)) {
            return ApiResponse.failure(
                    "Please send valid token for you app instance. " +
                            "The token value is available in the DROVE_APP_INSTANCE_AUTH_TOKEN environment variable");
        }
        val states = null == requiredStates || requiredStates.isEmpty()
                     ? RUNNING_STATES
                     : requiredStates;
        val relevantAppIds = forApp
                             ? appDB.application(info.getAppId())
                                     .map(appInfo -> appInfo.getSpec().getName())
                                     .map(appName -> appDB.applications(0, Integer.MAX_VALUE)
                                             .stream()
                                             .filter(appInfo -> appInfo.getSpec().getName().equals(appName))
                                             .map(ApplicationInfo::getAppId)
                                             .collect(Collectors.toUnmodifiableSet()))
                                     .orElse(Set.of())
                             : Set.of(info.getAppId());

        return ApiResponse.success(instanceInfoDB.instances(relevantAppIds, states)
                                           .values()
                                           .stream()
                                           .flatMap(Collection::stream)
                                           .sorted(Comparator.comparing(InstanceInfo::getInstanceId))
                                           .toList());
    }

    private boolean hasAccess(DroveApplicationInstanceInfo info) {
        return instanceInfoDB.instance(info.getAppId(), info.getInstanceId())
                .filter(instanceInfo -> instanceInfo.getExecutorId().equals(info.getExecutorId()))
                .isPresent();
    }

    private DroveApplicationInstanceInfo extractInstanceInfo(DroveUser droveUser) {
        return droveUser.accept(new DroveUserVisitorAdaptor<>(null) {
            @Override
            public DroveApplicationInstanceInfo visit(DroveApplicationInstance applicationInstance) {
                return applicationInstance.getTokenInfo();
            }
        });
    }
}
