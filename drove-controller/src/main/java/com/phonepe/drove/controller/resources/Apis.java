package com.phonepe.drove.controller.resources;

import com.phonepe.drove.controller.engine.ApplicationEngine;
import com.phonepe.drove.controller.engine.CommandValidator;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.api.*;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.*;

/**
 *
 */
@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Singleton
@Slf4j
public class Apis {
    private static final EnumSet<InstanceState> ACTIVE_STATES = EnumSet.of(InstanceState.PENDING,
                                                                           InstanceState.PROVISIONING,
                                                                           InstanceState.STARTING,
                                                                           InstanceState.UNHEALTHY,
                                                                           InstanceState.HEALTHY,
                                                                           InstanceState.DEPROVISIONING,
                                                                           InstanceState.STOPPING);
    private final ApplicationEngine engine;
    private final ResponseEngine responseEngine;

    @Inject
    public Apis(ApplicationEngine engine, ResponseEngine responseEngine) {
        this.engine = engine;
        this.responseEngine = responseEngine;
    }

    @POST
    @Path("/operations")
    public ApiResponse<Map<String, String>> acceptOperation(@NotNull @Valid final ApplicationOperation operation) {
        val res = engine.handleOperation(operation);
        if(res.getStatus().equals(CommandValidator.ValidationStatus.SUCCESS)) {
            return ApiResponse.success(Collections.singletonMap("appId", ControllerUtils.appId(operation)));
        }
        return new ApiResponse<>(ApiErrorCode.FAILED, null, res.getMessage());
    }

    @GET
    @Path("/applications")
    public ApiResponse<Map<String, AppSummary>> applications(
            @QueryParam("from") @DefaultValue("0") @Min(0) @Max(1024) final int from,
            @QueryParam("size") @DefaultValue("1024") @Min(0) @Max(1024) final int size) {
        return responseEngine.applications(from, size);
    }

    @GET
    @Path("/applications/{id}")
    public ApiResponse<AppSummary> application(@PathParam("id") @NotEmpty final String appId) {
        return responseEngine.application(appId);
    }

    @GET
    @Path("/applications/{id}/instances")
    public ApiResponse<List<InstanceInfo>> applicationInstances(
            @PathParam("id") @NotEmpty final String appId,
            @QueryParam("state") final Set<InstanceState> state) {

        return responseEngine.applicationInstances(appId, state);
    }

    @GET
    @Path("/cluster")
    public ApiResponse<ClusterSummary> clusterSummary() {
        return responseEngine.cluster();
    }

    @GET
    @Path("/cluster/executors")
    public ApiResponse<List<ExecutorSummary>> nodes() {
        return responseEngine.nodes();
    }

    @GET
    @Path("/cluster/executors/{id}")
    public ApiResponse<ExecutorNodeData> executorDetails(@PathParam("id") @NotEmpty final String executorId) {
        return responseEngine.executorDetails(executorId);
    }

    @GET
    @Path("/endpoints")
    public ApiResponse<List<ExposedAppInfo>> endpoints() {
        return responseEngine.endpoints();
    }
}
