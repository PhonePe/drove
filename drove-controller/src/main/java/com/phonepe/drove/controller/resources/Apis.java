package com.phonepe.drove.controller.resources;

import com.codahale.metrics.annotation.Timed;
import com.phonepe.drove.common.auth.DroveUserRole;
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

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 */
@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Singleton
@Slf4j
@PermitAll
public class Apis {

    private final ApplicationEngine engine;
    private final ResponseEngine responseEngine;

    @Inject
    public Apis(
            ApplicationEngine engine,
            ResponseEngine responseEngine) {
        this.engine = engine;
        this.responseEngine = responseEngine;
    }

    @POST
    @Path("/operations")
    @Timed
    @RolesAllowed(DroveUserRole.Values.DROVE_EXTERNAL_READ_WRITE_ROLE)
    public ApiResponse<Map<String, String>> acceptOperation(@NotNull @Valid final ApplicationOperation operation) {
        val res = engine.handleOperation(operation);
        if (res.getStatus().equals(CommandValidator.ValidationStatus.SUCCESS)) {
            return ApiResponse.success(Collections.singletonMap("appId", ControllerUtils.appId(operation)));
        }
        return ApiResponse.failure(res.getMessage());
    }

    @POST
    @Path("/operations/{appId}/cancel")
    @Timed
    @RolesAllowed(DroveUserRole.Values.DROVE_EXTERNAL_READ_WRITE_ROLE)
    public ApiResponse<Void> cancelJobForCurrentOp(@PathParam("appId") @NotEmpty final String appId) {
        return engine.cancelCurrentJob(appId)
               ? ApiResponse.success(null)
               : ApiResponse.failure("Current operation could not be cancelled");
    }

    @GET
    @Path("/applications")
    @Timed
    public ApiResponse<Map<String, AppSummary>> applications(
            @QueryParam("from") @DefaultValue("0") @Min(0) @Max(1024) final int from,
            @QueryParam("size") @DefaultValue("1024") @Min(0) @Max(1024) final int size) {
        return responseEngine.applications(from, size);
    }

    @GET
    @Path("/applications/{id}")
    @Timed
    public ApiResponse<AppSummary> application(@PathParam("id") @NotEmpty final String appId) {
        return responseEngine.application(appId);
    }

    @GET
    @Path("/applications/{id}/instances")
    @Timed
    public ApiResponse<List<InstanceInfo>> applicationInstances(
            @PathParam("id") @NotEmpty final String appId,
            @QueryParam("state") final Set<InstanceState> state) {

        return responseEngine.applicationInstances(appId, state);
    }

    @GET
    @Path("/applications/{id}/instances/old")
    @Timed
    public ApiResponse<List<InstanceInfo>> applicationOldInstances(
            @PathParam("id") @NotEmpty final String appId) {

        return responseEngine.applicationOldInstances(appId);
    }

    @GET
    @Path("/cluster")
    @Timed
    public ApiResponse<ClusterSummary> clusterSummary() {
        return responseEngine.cluster();
    }

    @GET
    @Path("/cluster/executors")
    @Timed
    public ApiResponse<List<ExecutorSummary>> nodes() {
        return responseEngine.nodes();
    }

    @GET
    @Path("/cluster/executors/{id}")
    @Timed
    public ApiResponse<ExecutorNodeData> executorDetails(@PathParam("id") @NotEmpty final String executorId) {
        return responseEngine.executorDetails(executorId);
    }

    @POST
    @Path("/cluster/executors/{id}/blacklist")
    @Timed
    @RolesAllowed(DroveUserRole.Values.DROVE_EXTERNAL_READ_WRITE_ROLE)
    public ApiResponse<Void> blacklistExecutor(@PathParam("id") @NotEmpty final String executorId) {
        return responseEngine.blacklistExecutor(executorId);
    }

    @POST
    @Path("/cluster/executors/{id}/unblacklist")
    @Timed
    @RolesAllowed(DroveUserRole.Values.DROVE_EXTERNAL_READ_WRITE_ROLE)
    public ApiResponse<Void> unblacklistExecutor(@PathParam("id") @NotEmpty final String executorId) {
        return responseEngine.unblacklistExecutor(executorId);
    }

    @GET
    @Path("/endpoints")
    @Timed
    public ApiResponse<List<ExposedAppInfo>> endpoints() {
        return responseEngine.endpoints();
    }

    @GET
    @Path("/ping")
    @Timed
    public ApiResponse<String> ping() {
        return ApiResponse.success("pong");
    }
}
