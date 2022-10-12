package com.phonepe.drove.controller.resources;

import com.codahale.metrics.annotation.Timed;
import com.phonepe.drove.auth.model.DroveUserRole;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.controller.engine.ApplicationEngine;
import com.phonepe.drove.controller.engine.TaskEngine;
import com.phonepe.drove.controller.engine.ValidationStatus;
import com.phonepe.drove.controller.statedb.ClusterStateDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.api.*;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.common.ClusterStateData;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.TaskOperation;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.validation.constraints.*;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.phonepe.drove.models.api.ApiResponse.success;

/**
 *
 */
@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Singleton
@Slf4j
@PermitAll
@Timed
public class Apis {

    private final ApplicationEngine engine;
    private final TaskEngine taskEngine;

    private final ResponseEngine responseEngine;
    private final ClusterStateDB clusterStateDB;


    @Inject
    public Apis(
            ApplicationEngine engine,
            TaskEngine taskEngine, ResponseEngine responseEngine,
            ClusterStateDB clusterStateDB) {
        this.engine = engine;
        this.taskEngine = taskEngine;
        this.responseEngine = responseEngine;
        this.clusterStateDB = clusterStateDB;
    }

    @POST
    @Path("/operations")
    @Timed
    @RolesAllowed(DroveUserRole.Values.DROVE_EXTERNAL_READ_WRITE_ROLE)
    public Response acceptOperation(@NotNull @Valid final ApplicationOperation operation) {
        return acceptAppOperation(operation);
    }

    @POST
    @Path("/operations/{appId}/cancel")
    @Timed
    @RolesAllowed(DroveUserRole.Values.DROVE_EXTERNAL_READ_WRITE_ROLE)
    public Response cancelJobForCurrentOp(@PathParam("appId") @NotEmpty final String appId) {
        return cancelJobForCurrentAppOp(appId);
    }

    @POST
    @Path("/applications/operations")
    @Timed
    @RolesAllowed(DroveUserRole.Values.DROVE_EXTERNAL_READ_WRITE_ROLE)
    public Response acceptAppOperation(@NotNull @Valid final ApplicationOperation operation) {
        if (CommonUtils.isInMaintenanceWindow(clusterStateDB.currentState().orElse(null))) {
            return ControllerUtils.badRequest(Map.of("validationErrors", List.of("Cluster is in maintenance mode")),
                                              "Command validation failure");
        }
        val res = engine.handleOperation(operation);
        if (res.getStatus().equals(ValidationStatus.SUCCESS)) {
            return ControllerUtils.ok(Map.of("appId", ControllerUtils.deployableObjectId(operation)));
        }
        return ControllerUtils.badRequest(Map.of("validationErrors", res.getMessages()), "Command validation failure");
    }

    @POST
    @Path("/applications/operations/{appId}/cancel")
    @Timed
    @RolesAllowed(DroveUserRole.Values.DROVE_EXTERNAL_READ_WRITE_ROLE)
    public Response cancelJobForCurrentAppOp(@PathParam("appId") @NotEmpty final String appId) {
        return engine.cancelCurrentJob(appId)
               ? ControllerUtils.ok(null)
               : ControllerUtils.badRequest(null, "Current operation could not be cancelled");
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
    @Path("/applications/{id}/spec")
    @Timed
    public ApiResponse<ApplicationSpec> applicationSpec(@PathParam("id") @NotEmpty final String appId) {
        return responseEngine.applicationSpec(appId);
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
    @Path("/applications/{appId}/instances/{instanceId}")
    @Timed
    public ApiResponse<InstanceInfo> applicationInstance(
            @PathParam("appId") @NotEmpty final String appId,
            @PathParam("instanceId") @NotEmpty final String instanceId) {
        return responseEngine.instanceDetails(appId, instanceId);
    }


    @GET
    @Path("/applications/{id}/instances/old")
    @Timed
    public ApiResponse<List<InstanceInfo>> applicationOldInstances(
            @PathParam("id") @NotEmpty final String appId,
            @QueryParam("start") @Min(0) @Max(1024) @DefaultValue("0") int start,
            @QueryParam("size") @Min(0) @Max(1024) @DefaultValue("1024") int size) {

        return responseEngine.applicationOldInstances(appId, start, size);
    }

    @POST
    @Path("/tasks/operations")
    @Timed
    @RolesAllowed(DroveUserRole.Values.DROVE_EXTERNAL_READ_WRITE_ROLE)
    public Response acceptTaskOperation(@NotNull @Valid final TaskOperation operation) {
        if (CommonUtils.isInMaintenanceWindow(clusterStateDB.currentState().orElse(null))) {
            return ControllerUtils.badRequest(Map.of("validationErrors", List.of("Cluster is in maintenance mode")),
                                              "Command validation failure");
        }
        val res = taskEngine.handleTaskOp(operation);
        if (res.getStatus().equals(ValidationStatus.SUCCESS)) {
            return ControllerUtils.ok(Map.of("taskId", ControllerUtils.deployableObjectId(operation)));
        }
        return ControllerUtils.badRequest(Map.of("validationErrors", res.getMessages()), "Command validation failure");
    }

    @GET
    @Path("/tasks")
    @Timed
    public ApiResponse<List<TaskInfo>> activeTasks() {
        return success(taskEngine.activeTasks());
    }

    @GET
    @Path("/tasks/{sourceAppName}/instances/{taskId}")
    @Timed
    public ApiResponse<TaskInfo> taskInstance(
            @PathParam("sourceAppName") @NotEmpty final String sourceAppName,
            @PathParam("taskId") @NotEmpty final String taskId) {
        return responseEngine.taskDetails(sourceAppName, taskId);
    }

    @DELETE
    @Path("/tasks/{sourceAppName}/instances/{taskId}")
    @Timed
    public ApiResponse<Map<String, Boolean>> deleteTaskInstance(
            @PathParam("sourceAppName") @NotEmpty final String sourceAppName,
            @PathParam("taskId") @NotEmpty final String taskId) {
        return responseEngine.taskDelete(sourceAppName, taskId);
    }

    @POST
    @Path("/tasks/search")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Timed
    public Response searchTask(
            @FormParam("taskSearchAppName") @Pattern(regexp = "[a-zA-Z\\d\\-_]*") @NotEmpty final String sourceAppName,
            @FormParam("taskSearchTaskID") @Pattern(regexp = "[a-zA-Z\\d\\-_]*") @NotEmpty final String taskId) {
        val redirectUri = responseEngine.taskDetails(sourceAppName, taskId)
                                  .getStatus()
                                  .equals(ApiErrorCode.SUCCESS)
                        ? "/tasks/" + sourceAppName + "/" + taskId
                        : "/";
        return Response.seeOther(URI.create(redirectUri))
                .build();
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

    @POST
    @Path("/cluster/maintenance/set")
    @Timed
    @RolesAllowed(DroveUserRole.Values.DROVE_EXTERNAL_READ_WRITE_ROLE)
    public ApiResponse<ClusterStateData> setClusterMaintenanceMode() {
        return responseEngine.setClusterMaintenanceMode();
    }

    @POST
    @Path("/cluster/maintenance/unset")
    @Timed
    @RolesAllowed(DroveUserRole.Values.DROVE_EXTERNAL_READ_WRITE_ROLE)
    public ApiResponse<ClusterStateData> unsetClusterMaintenanceMode() {
        return responseEngine.unsetClusterMaintenanceMode();
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
        return success("pong");
    }

}
