package com.phonepe.drove.controller.resources;

import com.codahale.metrics.annotation.Timed;
import com.phonepe.drove.auth.model.*;
import com.phonepe.drove.controller.statedb.InstanceInfoDB;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import io.dropwizard.auth.Auth;
import lombok.val;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 *
 */
@Path("/v1/internal")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed(DroveUserRole.Values.DROVE_APPLICATION_INSTANCE_ROLE)
@Timed
public class AppSupport {

    private final InstanceInfoDB instanceInfoDB;

    @Inject
    public AppSupport(InstanceInfoDB instanceInfoDB) {
        this.instanceInfoDB = instanceInfoDB;
    }

    @GET
    @Path("/instances")
    public ApiResponse<List<InstanceInfo>> siblingInstances(@Auth final DroveUser droveUser) {
        val info = extractInstanceInfo(droveUser);
        if (info == null) {
            return ApiResponse.failure(
                    "This api is applicable for calls by app instances from inside the cluster only");
        }
        if(!hasAccess(info)) {
            return ApiResponse.failure(
                    "Please send valid token for you app instance. " +
                            "The token value is available in the DROVE_APP_INSTANCE_AUTH_TOKEN environment variable");
        }
        return ApiResponse.success(instanceInfoDB.instances(Set.of(info.getAppId()), InstanceState.ACTIVE_STATES)
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
        return droveUser.accept(new DroveUserVisitor<>() {
            @Override
            public DroveApplicationInstanceInfo visit(DroveClusterNode clusterNode) {
                return null;
            }

            @Override
            public DroveApplicationInstanceInfo visit(DroveApplicationInstance applicationInstance) {
                return applicationInstance.getTokenInfo();
            }

            @Override
            public DroveApplicationInstanceInfo visit(DroveExternalUser externalUser) {
                return null;
            }
        });
    }
}
