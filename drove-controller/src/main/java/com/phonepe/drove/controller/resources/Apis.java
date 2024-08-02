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

package com.phonepe.drove.controller.resources;

import com.codahale.metrics.annotation.Timed;
import com.phonepe.drove.auth.model.DroveUser;
import com.phonepe.drove.auth.model.DroveUserRole;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.controller.engine.ApplicationEngine;
import com.phonepe.drove.controller.engine.TaskEngine;
import com.phonepe.drove.controller.engine.ValidationStatus;
import com.phonepe.drove.controller.masking.EnforceMasking;
import com.phonepe.drove.controller.statedb.ClusterStateDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.api.*;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.common.ClusterStateData;
import com.phonepe.drove.models.events.DroveEvent;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.TaskOperation;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import io.dropwizard.auth.Auth;
import lombok.SneakyThrows;
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

    public static final int MAX_ELEMENTS = Integer.MAX_VALUE - 1;
    public static final String MAX_ELEMENTS_TEXT = "2147483646";

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
    public Response acceptOperation(
            @Auth final DroveUser user,
            @NotNull @Valid final ApplicationOperation operation) {
        return acceptAppOperation(user, operation);
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
    public Response acceptAppOperation(
            @Auth final DroveUser user,
            @NotNull @Valid final ApplicationOperation operation) {
        if (CommonUtils.isInMaintenanceWindow(clusterStateDB.currentState().orElse(null))) {
            return ControllerUtils.commandValidationFailure("Cluster is in maintenance mode");
        }
        val res = engine.handleOperation(operation);
        log.info("ACCESS_AUDIT: Application Operation {} received from user: {}. Validation result: {}",
                 operation, user.getName(), res);
        if (res.getStatus().equals(ValidationStatus.SUCCESS)) {
            return ControllerUtils.ok(Map.of("appId", ControllerUtils.deployableObjectId(operation)));
        }
        return ControllerUtils.commandValidationFailure(res.getMessages());
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
            @QueryParam("from") @DefaultValue("0") @Min(0) @Max(MAX_ELEMENTS) final int from,
            @QueryParam("size") @DefaultValue(MAX_ELEMENTS_TEXT) @Min(0) @Max(MAX_ELEMENTS) final int size) {
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
    @SneakyThrows
    @EnforceMasking
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
            @QueryParam("start") @Min(0) @Max(MAX_ELEMENTS) @DefaultValue("0") int start,
            @QueryParam("size") @Min(0) @Max(MAX_ELEMENTS) @DefaultValue(MAX_ELEMENTS_TEXT) int size) {

        return responseEngine.applicationOldInstances(appId, start, size);
    }

    @POST
    @Path("/tasks/operations")
    @Timed
    @RolesAllowed(DroveUserRole.Values.DROVE_EXTERNAL_READ_WRITE_ROLE)
    public Response acceptTaskOperation(
            @Auth final DroveUser user,
            @NotNull @Valid final TaskOperation operation) {
        if (CommonUtils.isInMaintenanceWindow(clusterStateDB.currentState().orElse(null))) {
            return ControllerUtils.commandValidationFailure("Cluster is in maintenance mode");
        }
        val res = taskEngine.handleTaskOp(operation);
        log.info("ACCESS_AUDIT: Task Operation {} received from user: {}. Validation result: {}",
                 operation, user.getName(), res);
        if (res.getStatus().equals(ValidationStatus.SUCCESS)) {
            return ControllerUtils.ok(Map.of("taskId", ControllerUtils.deployableObjectId(operation)));
        }
        return ControllerUtils.commandValidationFailure(res.getMessages());
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
    public ApiResponse<Map<String, Set<String>>> blacklistExecutor(@PathParam("id") @NotEmpty final String executorId) {
        return responseEngine.blacklistExecutors(Set.of(executorId));
    }

    @POST
    @Path("/cluster/executors/blacklist")
    @Timed
    @RolesAllowed(DroveUserRole.Values.DROVE_EXTERNAL_READ_WRITE_ROLE)
    public ApiResponse<Map<String, Set<String>>> blacklistExecutors(
            @QueryParam("id") @NotEmpty final Set<String> executorIds) {
        return responseEngine.blacklistExecutors(executorIds);
    }

    @POST
    @Path("/cluster/executors/{id}/unblacklist")
    @Timed
    @RolesAllowed(DroveUserRole.Values.DROVE_EXTERNAL_READ_WRITE_ROLE)
    public ApiResponse<Map<String, Set<String>>> unblacklistExecutor(@PathParam("id") @NotEmpty final String executorId) {
        return responseEngine.unblacklistExecutors(Set.of(executorId));
    }

    @POST
    @Path("/cluster/executors/unblacklist")
    @Timed
    @RolesAllowed(DroveUserRole.Values.DROVE_EXTERNAL_READ_WRITE_ROLE)
    public ApiResponse<Map<String, Set<String>>> unblacklistExecutors(
            @QueryParam("id") @NotEmpty final Set<String> executorIds) {
        return responseEngine.unblacklistExecutors(executorIds);
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

    /**
     * @param lastSyncTime Last time sync happened
     * @param size         number of events
     * @return List of events
     * @deprecated use the /latest api instead
     */
    @GET
    @Path("/cluster/events")
    @Timed
    @SuppressWarnings("rawtypes")
    @Deprecated(since = "1.27", forRemoval = true)
    public ApiResponse<List<DroveEvent>> events(
            @QueryParam("lastSyncTime") @DefaultValue("0") @Min(0) @Max(Long.MAX_VALUE) long lastSyncTime,
            @QueryParam("size") @DefaultValue("1024") @Min(0) @Max(Integer.MAX_VALUE) int size) {
        return responseEngine.events(lastSyncTime, size);
    }

    @GET
    @Path("/cluster/events/latest")
    @Timed
    public ApiResponse<DroveEventsList> eventsList(
            @QueryParam("lastSyncTime") @DefaultValue("0") @Min(0) @Max(Long.MAX_VALUE) long lastSyncTime,
            @QueryParam("size") @DefaultValue("1024") @Min(0) @Max(Integer.MAX_VALUE) int size) {
        return responseEngine.eventList(lastSyncTime, size);
    }

    @GET
    @Path("/cluster/events/summary")
    @Timed
    public ApiResponse<DroveEventsSummary> eventsList(
            @QueryParam("lastSyncTime") @DefaultValue("0") @Min(0) @Max(Long.MAX_VALUE) long lastSyncTime) {
        return responseEngine.summarize(lastSyncTime);
    }

    @GET
    @Path("/endpoints")
    @Timed
    public ApiResponse<List<ExposedAppInfo>> endpoints() {
        return responseEngine.endpoints();
    }

    @GET
    @Path("/endpoints/app/{appName}")
    @Timed
    public ApiResponse<List<ExposedAppInfo>> endpoints(@PathParam("appName") @NotEmpty final String appName) {
        return responseEngine.endpoints(appName);
    }

    @GET
    @Path("/ping")
    @Timed
    public ApiResponse<String> ping() {
        return success("pong");
    }

}
