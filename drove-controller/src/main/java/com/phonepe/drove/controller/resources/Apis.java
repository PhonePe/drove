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
import com.phonepe.drove.auth.model.DroveUser;
import com.phonepe.drove.auth.model.DroveUserRole;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.controller.engine.ApplicationLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.LocalServiceLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.TaskEngine;
import com.phonepe.drove.controller.engine.ValidationStatus;
import com.phonepe.drove.controller.engine.ValidationResult;
import com.phonepe.drove.controller.masking.EnforceMasking;
import com.phonepe.drove.models.api.DryRunRuleInfo;
import com.phonepe.drove.controller.rule.RuleEvaluator;
import com.phonepe.drove.controller.statedb.ClusterStateDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.api.*;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.common.ClusterStateData;
import com.phonepe.drove.models.events.DroveEvent;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import com.phonepe.drove.models.localservice.LocalServiceSpec;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.LocalServiceOperation;
import com.phonepe.drove.models.operation.TaskOperation;
import com.phonepe.drove.models.operation.rule.RuleCallStatus;
import com.phonepe.drove.models.task.TaskSpec;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.phonepe.drove.models.api.ApiResponse.success;

/**
 * Drove Controller REST API
 */
@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Singleton
@Slf4j
@PermitAll
@Timed
@Tag(name = "Drove Controller API", description = "REST API for Drove Container Orchestrator")
public class Apis {

    public static final int MAX_ELEMENTS = Integer.MAX_VALUE - 1;
    public static final String MAX_ELEMENTS_TEXT = "2147483646";

    private final ApplicationLifecycleManagementEngine applicationEngine;
    private final TaskEngine taskEngine;
    private final LocalServiceLifecycleManagementEngine localServiceEngine;

    private final ResponseEngine responseEngine;
    private final ClusterStateDB clusterStateDB;
    private final RuleEvaluator ruleEvaluator;


    @Inject
    public Apis(
            ApplicationLifecycleManagementEngine applicationEngine,
            TaskEngine taskEngine,
            LocalServiceLifecycleManagementEngine localServiceEngine,
            ResponseEngine responseEngine,
            ClusterStateDB clusterStateDB,
            RuleEvaluator ruleEvaluator) {
        this.applicationEngine = applicationEngine;
        this.taskEngine = taskEngine;
        this.localServiceEngine = localServiceEngine;
        this.responseEngine = responseEngine;
        this.clusterStateDB = clusterStateDB;
        this.ruleEvaluator = ruleEvaluator;
    }

    @POST
    @Path("/applications/validate/spec")
    @Timed
    @Operation(summary = "Validate application spec", description = "Validates an application deployment specification without deploying it", tags = {"Applications"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Validation result")
    public Response validateAppSpec(
            @Auth final DroveUser user,
            @NotNull @Valid final ApplicationSpec spec) {
        return processRequest(spec,
                              applicationEngine::validateSpec,
                              (s, res) -> log.info("ACCESS_AUDIT: Application Spec {} received from user: {}. Validation result: {}",
                                                   s.getName(), user.getName(), res),
                              s -> Map.of("valid", true));
    }

    @POST
    @Path("/applications/placementpolicy/rule/dryrun")
    @Timed
    @Operation(summary = "Dry run placement rule", description = "Tests a placement policy rule without actually applying it", tags = {"Applications"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Dry run result with check and evaluation status")
    public Response validatePlacementRule(
            @Auth final DroveUser user,
            @Valid @NotNull final DryRunRuleInfo dryRunRuleInfo) {

        if ( CommonUtils.isInMaintenanceWindow(clusterStateDB.currentState().orElse(null))) {
            return maintenanceModeError();
        }
        val policy = dryRunRuleInfo.getRuleBasedPlacementPolicy();
        val schedulingInfo = dryRunRuleInfo.getSchedulingInfo();

        val checkResponse = ruleEvaluator.check(policy);
        if (RuleCallStatus.SUCCESS != checkResponse.getStatus()) {
            return ControllerUtils.ok(Map.of(
                    "status", false,
                    "checkResult", checkResponse.getError(),
                    "evalResult", null));
        }

        val evalResponse = ruleEvaluator.evaluate(policy, schedulingInfo);

        log.info("ACCESS_AUDIT: Placement rule dryrun: {} received from user: {}. Validation result: {}",
                policy.getRule(), user.getName(), evalResponse.isResult());

        return ControllerUtils.ok(Map.of(
                "status", evalResponse.getStatus() == RuleCallStatus.SUCCESS,
                "checkResult", checkResponse,
                "evalResult", evalResponse));
    }

    @POST
    @Path("/operations")
    @Timed
    @RolesAllowed({DroveUserRole.Values.DROVE_EXTERNAL_READ_WRITE_ROLE, DroveUserRole.Values.DROVE_EXTERNAL_ROOT_ROLE})
    @Operation(summary = "Submit application operation (deprecated)", description = "Submit an application operation. Use /applications/operations instead", tags = {"Applications"}, deprecated = true)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Operation accepted")
    public Response acceptOperation(
            @Auth final DroveUser user,
            @NotNull @Valid final ApplicationOperation operation) {
        return acceptAppOperation(user, operation);
    }

    @POST
    @Path("/operations/{appId}/cancel")
    @Timed
    @RolesAllowed({DroveUserRole.Values.DROVE_EXTERNAL_READ_WRITE_ROLE, DroveUserRole.Values.DROVE_EXTERNAL_ROOT_ROLE})
    @Operation(summary = "Cancel current operation (deprecated)", description = "Cancel current operation for an app. Use /applications/operations/{appId}/cancel instead", tags = {"Applications"}, deprecated = true)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Operation cancelled")
    public Response cancelJobForCurrentOp(@PathParam("appId") @NotEmpty @Parameter(description = "Application ID") final String appId) {
        return cancelJobForCurrentAppOp(appId);
    }

    @POST
    @Path("/applications/operations")
    @Timed
    @RolesAllowed({DroveUserRole.Values.DROVE_EXTERNAL_READ_WRITE_ROLE, DroveUserRole.Values.DROVE_EXTERNAL_ROOT_ROLE})
    @Operation(summary = "Submit application operation", description = "Submit an application lifecycle operation (CREATE, DESTROY, START_INSTANCES, STOP_INSTANCES, SCALE, REPLACE_INSTANCES, SUSPEND, RECOVER)", tags = {"Applications"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Operation accepted with application ID")
    public Response acceptAppOperation(
            @Auth final DroveUser user,
            @NotNull @Valid final ApplicationOperation operation) {
        return processRequest(operation,
                              applicationEngine::handleOperation,
                              (op, res) -> log.info("ACCESS_AUDIT: Application Operation {} received from user: {}. Validation result: {}",
                                                    op, user.getName(), res),
                              op -> Map.of("appId", ControllerUtils.deployableObjectId(op)));
    }

    @POST
    @Path("/applications/operations/{appId}/cancel")
    @Timed
    @RolesAllowed({DroveUserRole.Values.DROVE_EXTERNAL_READ_WRITE_ROLE, DroveUserRole.Values.DROVE_EXTERNAL_ROOT_ROLE})
    @Operation(summary = "Cancel application operation", description = "Cancel the currently running operation for an application", tags = {"Applications"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Operation cancelled successfully")
    public Response cancelJobForCurrentAppOp(@PathParam("appId") @NotEmpty @Parameter(description = "Application ID") final String appId) {
        return applicationEngine.cancelCurrentJob(appId)
               ? ControllerUtils.ok(null)
               : ControllerUtils.badRequest(null, "Current operation could not be cancelled");
    }

    @GET
    @Path("/applications")
    @Timed
    @Operation(summary = "List applications", description = "Get a paginated list of all applications with their summaries", tags = {"Applications"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Map of application IDs to their summaries")
    public ApiResponse<Map<String, AppSummary>> applications(
            @QueryParam("from") @DefaultValue("0") @Min(0) @Max(MAX_ELEMENTS) @Parameter(description = "Pagination start index") final int from,
            @QueryParam("size") @DefaultValue(MAX_ELEMENTS_TEXT) @Min(0) @Max(MAX_ELEMENTS) @Parameter(description = "Number of items to return") final int size) {
        return responseEngine.applications(from, size);
    }

    @GET
    @Path("/applications/{id}")
    @Timed
    @Operation(summary = "Get application details", description = "Get detailed summary of a specific application", tags = {"Applications"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Application summary")
    public ApiResponse<AppSummary> application(@PathParam("id") @NotEmpty @Parameter(description = "Application ID") final String appId) {
        return responseEngine.application(appId);
    }

    @GET
    @Path("/applications/{id}/spec")
    @Timed
    @SneakyThrows
    @EnforceMasking
    @Operation(summary = "Get application spec", description = "Get the deployment specification of an application", tags = {"Applications"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Application specification")
    public ApiResponse<ApplicationSpec> applicationSpec(@PathParam("id") @NotEmpty @Parameter(description = "Application ID") final String appId) {
        return responseEngine.applicationSpec(appId);
    }

    @GET
    @Path("/applications/{id}/instances")
    @Timed
    @Operation(summary = "List application instances", description = "Get all instances of an application, optionally filtered by state", tags = {"Applications"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of application instances")
    public ApiResponse<List<InstanceInfo>> applicationInstances(
            @PathParam("id") @NotEmpty @Parameter(description = "Application ID") final String appId,
            @QueryParam("state") @Parameter(description = "Filter by instance states") final Set<InstanceState> state) {

        return responseEngine.applicationInstances(appId, state);
    }

    @GET
    @Path("/applications/{appId}/instances/{instanceId}")
    @Timed
    @Operation(summary = "Get instance details", description = "Get detailed information about a specific application instance", tags = {"Applications"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Instance details")
    public ApiResponse<InstanceInfo> applicationInstance(
            @PathParam("appId") @NotEmpty @Parameter(description = "Application ID") final String appId,
            @PathParam("instanceId") @NotEmpty @Parameter(description = "Instance ID") final String instanceId) {
        return responseEngine.instanceDetails(appId, instanceId);
    }


    @GET
    @Path("/applications/{id}/instances/old")
    @Timed
    @Operation(summary = "List old instances", description = "Get historical instances that are no longer active", tags = {"Applications"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of old instances")
    public ApiResponse<List<InstanceInfo>> applicationOldInstances(
            @PathParam("id") @NotEmpty @Parameter(description = "Application ID") final String appId,
            @QueryParam("start") @Min(0) @Max(MAX_ELEMENTS) @DefaultValue("0") @Parameter(description = "Pagination start index") int start,
            @QueryParam("size") @Min(0) @Max(MAX_ELEMENTS) @DefaultValue(MAX_ELEMENTS_TEXT) @Parameter(description = "Number of items to return") int size) {

        return responseEngine.applicationOldInstances(appId, start, size);
    }

    @POST
    @Path("/localservices/validate/spec")
    @Timed
    @Operation(summary = "Validate local service spec", description = "Validates a local service deployment specification without deploying it", tags = {"Local Services"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Validation result")
    public Response validateLocalServiceSpec(
            @Auth final DroveUser user,
            @NotNull @Valid final LocalServiceSpec spec) {
        return processRequest(spec,
                              localServiceEngine::validateSpec,
                              (s, res) -> log.info("ACCESS_AUDIT: Local Service Spec {} received from user: {}. Validation result: {}",
                                                   s.getName(), user.getName(), res),
                              s -> Map.of("valid", true));
    }

    @POST
    @Path("/localservices/operations")
    @Timed
    @RolesAllowed({DroveUserRole.Values.DROVE_EXTERNAL_READ_WRITE_ROLE, DroveUserRole.Values.DROVE_EXTERNAL_ROOT_ROLE})
    @Operation(summary = "Submit local service operation", description = "Submit a local service lifecycle operation (CREATE, DESTROY)", tags = {"Local Services"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Operation accepted with service ID")
    public Response acceptLocalServiceOperation(
            @Auth final DroveUser user,
            @NotNull @Valid final LocalServiceOperation operation) {
        return processRequest(operation,
                              localServiceEngine::handleOperation,
                              (op, res) -> log.info("ACCESS_AUDIT: Local Service Operation {} received from user: {}. Validation result: {}",
                                                    op, user.getName(), res),
                              op -> Map.of("serviceId", ControllerUtils.deployableObjectId(op)));
    }

    @POST
    @Path("/localservices/operations/{serviceId}/cancel")
    @Timed
    @RolesAllowed({DroveUserRole.Values.DROVE_EXTERNAL_READ_WRITE_ROLE, DroveUserRole.Values.DROVE_EXTERNAL_ROOT_ROLE})
    @Operation(summary = "Cancel local service operation", description = "Cancel the currently running operation for a local service", tags = {"Local Services"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Operation cancelled successfully")
    public Response cancelJobForCurrentLocalServiceOp(@PathParam("serviceId") @NotEmpty @Parameter(description = "Service ID") final String serviceId) {
        return localServiceEngine.cancelCurrentJob(serviceId)
               ? ControllerUtils.ok(null)
               : ControllerUtils.badRequest(null, "Current operation could not be cancelled");
    }

    @GET
    @Path("/localservices")
    @Timed
    @Operation(summary = "List local services", description = "Get a paginated list of all local services with their summaries", tags = {"Local Services"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Map of service IDs to their summaries")
    public ApiResponse<Map<String, LocalServiceSummary>> localServices(
            @QueryParam("from") @DefaultValue("0") @Min(0) @Max(MAX_ELEMENTS) @Parameter(description = "Pagination start index") final int from,
            @QueryParam("size") @DefaultValue(MAX_ELEMENTS_TEXT) @Min(0) @Max(MAX_ELEMENTS) @Parameter(description = "Number of items to return") final int size) {
        return responseEngine.localServices(from, size);
    }

    @GET
    @Path("/localservices/{serviceId}")
    @Timed
    @Operation(summary = "Get local service details", description = "Get detailed summary of a specific local service", tags = {"Local Services"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Local service summary")
    public ApiResponse<LocalServiceSummary> localService(@PathParam("serviceId") @NotEmpty @Parameter(description = "Service ID") final String serviceId) {
        return responseEngine.localService(serviceId);
    }

    @GET
    @Path("/localservices/{id}/spec")
    @Timed
    @SneakyThrows
    @EnforceMasking
    @Operation(summary = "Get local service spec", description = "Get the deployment specification of a local service", tags = {"Local Services"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Local service specification")
    public ApiResponse<LocalServiceSpec> localServiceSpec(@PathParam("id") @NotEmpty @Parameter(description = "Service ID") final String serviceId) {
        return responseEngine.localServiceSpec(serviceId);
    }

    @GET
    @Path("/localservices/{id}/instances")
    @Timed
    @Operation(summary = "List local service instances", description = "Get all instances of a local service, optionally filtered by state", tags = {"Local Services"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of local service instances")
    public ApiResponse<List<LocalServiceInstanceInfo>> locaServiceInstances(
            @PathParam("id") @NotEmpty @Parameter(description = "Service ID") final String serviceId,
            @QueryParam("state") @Parameter(description = "Filter by instance states") final Set<LocalServiceInstanceState> state) {

        return responseEngine.localServiceInstances(serviceId, state);
    }

    @GET
    @Path("/localservices/{serviceId}/instances/{instanceId}")
    @Timed
    @Operation(summary = "Get local service instance details", description = "Get detailed information about a specific local service instance", tags = {"Local Services"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Local service instance details")
    public ApiResponse<LocalServiceInstanceInfo> localServiceInstances(
            @PathParam("serviceId") @NotEmpty @Parameter(description = "Service ID") final String serviceId,
            @PathParam("instanceId") @NotEmpty @Parameter(description = "Instance ID") final String instanceId) {
        return responseEngine.localServiceInstanceDetails(serviceId, instanceId);
    }


    @GET
    @Path("/localservices/{serviceId}/instances/old")
    @Timed
    @Operation(summary = "List old local service instances", description = "Get historical instances of a local service that are no longer active", tags = {"Local Services"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of old local service instances")
    public ApiResponse<List<LocalServiceInstanceInfo>> localServiceOldInstances(
            @PathParam("serviceId") @NotEmpty @Parameter(description = "Service ID") final String serviceId) {
        return responseEngine.localServiceOldInstances(serviceId);
    }

    @POST
    @Path("/tasks/validate/spec")
    @Timed
    @Operation(summary = "Validate task spec", description = "Validates a task specification without executing it", tags = {"Tasks"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Validation result")
    public Response validateTaskSpec(
            @Auth final DroveUser user,
            @NotNull @Valid final TaskSpec taskSpec) {
        return processRequest(taskSpec,
                              taskEngine::validateSpec,
                              (s, res) -> log.info("ACCESS_AUDIT: Task Operation {}/{} received from user: {}. Validation result: {}",
                                                   s.getSourceAppName(), s.getTaskId(), user.getName(), res),
                              s -> null);
    }

    @POST
    @Path("/tasks/operations")
    @Timed
    @RolesAllowed({DroveUserRole.Values.DROVE_EXTERNAL_READ_WRITE_ROLE, DroveUserRole.Values.DROVE_EXTERNAL_ROOT_ROLE})
    @Operation(summary = "Submit task operation", description = "Submit a task operation (CREATE, KILL)", tags = {"Tasks"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Operation accepted with task ID")
    public Response acceptTaskOperation(
            @Auth final DroveUser user,
            @NotNull @Valid final TaskOperation operation) {
        return processRequest(operation,
                              taskEngine::handleTaskOp,
                              (op, res) -> log.info("ACCESS_AUDIT: Task Operation {} received from user: {}. Validation result: {}",
                                                    op, user.getName(), res),
                              op -> Map.of("taskId", ControllerUtils.deployableObjectId(op)));
    }

    @GET
    @Path("/tasks")
    @Timed
    @Operation(summary = "List active tasks", description = "Get all currently active tasks", tags = {"Tasks"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of active tasks")
    public ApiResponse<List<TaskInfo>> activeTasks() {
        return success(taskEngine.activeTasks());
    }

    @GET
    @Path("/tasks/{sourceAppName}/instances/{taskId}")
    @Timed
    @Operation(summary = "Get task details", description = "Get detailed information about a specific task instance", tags = {"Tasks"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Task instance details")
    public ApiResponse<TaskInfo> taskInstance(
            @PathParam("sourceAppName") @NotEmpty @Parameter(description = "Source application name") final String sourceAppName,
            @PathParam("taskId") @NotEmpty @Parameter(description = "Task ID") final String taskId) {
        return responseEngine.taskDetails(sourceAppName, taskId);
    }

    @DELETE
    @Path("/tasks/{sourceAppName}/instances/{taskId}")
    @Timed
    @Operation(summary = "Delete task instance", description = "Delete a task instance from the system", tags = {"Tasks"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Task deletion result")
    public ApiResponse<Map<String, Boolean>> deleteTaskInstance(
            @PathParam("sourceAppName") @NotEmpty @Parameter(description = "Source application name") final String sourceAppName,
            @PathParam("taskId") @NotEmpty @Parameter(description = "Task ID") final String taskId) {
        return responseEngine.taskDelete(sourceAppName, taskId);
    }

    @POST
    @Path("/tasks/search")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Timed
    @Operation(summary = "Search for task", description = "Search for a task by source app name and task ID, redirects to task details if found", tags = {"Tasks"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "303", description = "Redirect to task details or home page")
    public Response searchTask(
            @FormParam("taskSearchAppName") @Pattern(regexp = "[a-zA-Z\\d\\-_]*") @NotEmpty @Parameter(description = "Source application name") final String sourceAppName,
            @FormParam("taskSearchTaskID") @Pattern(regexp = "[a-zA-Z\\d\\-_]*") @NotEmpty @Parameter(description = "Task ID") final String taskId) {
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
    @Operation(summary = "Get cluster summary", description = "Get an overview of the cluster state including leader info and resource usage", tags = {"Cluster"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cluster summary")
    public ApiResponse<ClusterSummary> clusterSummary() {
        return responseEngine.cluster();
    }

    @GET
    @Path("/cluster/executors")
    @Timed
    @Operation(summary = "List executors", description = "Get a list of all executor nodes in the cluster", tags = {"Cluster"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of executor summaries")
    public ApiResponse<List<ExecutorSummary>> nodes() {
        return responseEngine.nodes();
    }

    @GET
    @Path("/cluster/executors/{id}")
    @Timed
    @Operation(summary = "Get executor details", description = "Get detailed information about a specific executor node", tags = {"Cluster"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Executor node details")
    public ApiResponse<ExecutorNodeData> executorDetails(@PathParam("id") @NotEmpty @Parameter(description = "Executor ID") final String executorId) {
        return responseEngine.executorDetails(executorId);
    }

    @POST
    @Path("/cluster/executors/{id}/blacklist")
    @Timed
    @RolesAllowed({DroveUserRole.Values.DROVE_EXTERNAL_MAINTENANCE_ROLE, DroveUserRole.Values.DROVE_EXTERNAL_ROOT_ROLE})
    @Operation(summary = "Blacklist executor", description = "Add an executor to the blacklist, preventing new deployments", tags = {"Cluster"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated blacklist status")
    public ApiResponse<Map<String, Set<String>>> blacklistExecutor(@PathParam("id") @NotEmpty @Parameter(description = "Executor ID") final String executorId) {
        return responseEngine.blacklistExecutors(Set.of(executorId));
    }

    @POST
    @Path("/cluster/executors/blacklist")
    @Timed
    @RolesAllowed({DroveUserRole.Values.DROVE_EXTERNAL_MAINTENANCE_ROLE, DroveUserRole.Values.DROVE_EXTERNAL_ROOT_ROLE})
    @Operation(summary = "Blacklist multiple executors", description = "Add multiple executors to the blacklist", tags = {"Cluster"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated blacklist status")
    public ApiResponse<BlacklistOperationResponse> blacklistExecutors(
            @QueryParam("id") @NotEmpty @Parameter(description = "Executor IDs to blacklist") final Set<String> executorIds) {
        return responseEngine.blacklistExecutors(executorIds);
    }

    @POST
    @Path("/cluster/executors/{id}/unblacklist")
    @Timed
    @RolesAllowed({DroveUserRole.Values.DROVE_EXTERNAL_MAINTENANCE_ROLE, DroveUserRole.Values.DROVE_EXTERNAL_ROOT_ROLE})
    @Operation(summary = "Unblacklist executor", description = "Remove an executor from the blacklist, allowing new deployments", tags = {"Cluster"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated blacklist status")
    public ApiResponse<Map<String, Set<String>>> unblacklistExecutor(@PathParam("id") @NotEmpty @Parameter(description = "Executor ID") final String executorId) {
        return responseEngine.unblacklistExecutors(Set.of(executorId));
    }

    @POST
    @Path("/cluster/executors/unblacklist")
    @Timed
    @RolesAllowed({DroveUserRole.Values.DROVE_EXTERNAL_MAINTENANCE_ROLE, DroveUserRole.Values.DROVE_EXTERNAL_ROOT_ROLE})
    @Operation(summary = "Unblacklist multiple executors", description = "Remove multiple executors from the blacklist", tags = {"Cluster"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated blacklist status")
    public ApiResponse<BlacklistOperationResponse> unblacklistExecutors(
            @QueryParam("id") @NotEmpty @Parameter(description = "Executor IDs to unblacklist") final Set<String> executorIds) {
        return responseEngine.unblacklistExecutors(executorIds);
    }

    @POST
    @Path("/cluster/maintenance/set")
    @Timed
    @RolesAllowed({DroveUserRole.Values.DROVE_EXTERNAL_MAINTENANCE_ROLE, DroveUserRole.Values.DROVE_EXTERNAL_ROOT_ROLE})
    @Operation(summary = "Enable maintenance mode", description = "Put the cluster into maintenance mode, blocking new operations", tags = {"Cluster"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated cluster state")
    public ApiResponse<ClusterStateData> setClusterMaintenanceMode() {
        return responseEngine.setClusterMaintenanceMode();
    }

    @POST
    @Path("/cluster/maintenance/unset")
    @Timed
    @RolesAllowed({DroveUserRole.Values.DROVE_EXTERNAL_MAINTENANCE_ROLE, DroveUserRole.Values.DROVE_EXTERNAL_ROOT_ROLE})
    @Operation(summary = "Disable maintenance mode", description = "Take the cluster out of maintenance mode, allowing operations to resume", tags = {"Cluster"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated cluster state")
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
    @Operation(summary = "Get cluster events (deprecated)", description = "Get cluster events since a given time. Use /cluster/events/latest instead", tags = {"Cluster"}, deprecated = true)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of cluster events")
    public ApiResponse<List<DroveEvent>> events(
            @QueryParam("lastSyncTime") @DefaultValue("0") @Min(0) @Max(Long.MAX_VALUE) @Parameter(description = "Timestamp to fetch events from") long lastSyncTime,
            @QueryParam("size") @DefaultValue("1024") @Min(0) @Max(Integer.MAX_VALUE) @Parameter(description = "Maximum number of events to return") int size) {
        return responseEngine.events(lastSyncTime, size);
    }

    @GET
    @Path("/cluster/events/latest")
    @Timed
    @Operation(summary = "Get latest cluster events", description = "Get the latest cluster events since a given time with event count metadata", tags = {"Cluster"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Events list with metadata")
    public ApiResponse<DroveEventsList> eventsList(
            @QueryParam("lastSyncTime") @DefaultValue("0") @Min(0) @Max(Long.MAX_VALUE) @Parameter(description = "Timestamp to fetch events from") long lastSyncTime,
            @QueryParam("size") @DefaultValue("1024") @Min(0) @Max(Integer.MAX_VALUE) @Parameter(description = "Maximum number of events to return") int size) {
        return responseEngine.eventList(lastSyncTime, size);
    }

    @GET
    @Path("/cluster/events/summary")
    @Timed
    @Operation(summary = "Get events summary", description = "Get a summary of events since a given time", tags = {"Cluster"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Events summary with counts")
    public ApiResponse<DroveEventsSummary> eventsList(
            @QueryParam("lastSyncTime") @DefaultValue("0") @Min(0) @Max(Long.MAX_VALUE) @Parameter(description = "Timestamp to fetch events from") long lastSyncTime) {
        return responseEngine.summarize(lastSyncTime);
    }

    @GET
    @Path("/endpoints")
    @Timed
    @Operation(summary = "List application endpoints", description = "Get exposed endpoint information for applications, optionally filtered by app names", tags = {"Endpoints"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of exposed application endpoints")
    public ApiResponse<List<ExposedAppInfo>> endpoints(
            @QueryParam("app") @Parameter(description = "Filter by application names") final Set<String> appNames) {
        return responseEngine.endpoints(appNames);
    }

    @GET
    @Path("/endpoints/app/{appName}")
    @Timed
    @Operation(summary = "Get application endpoints", description = "Get exposed endpoint information for a specific application", tags = {"Endpoints"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Application endpoint details")
    public ApiResponse<List<ExposedAppInfo>> endpoints(@PathParam("appName") @NotEmpty @Parameter(description = "Application name") final String appName) {
        return responseEngine.endpoints(Set.of(appName));
    }

    @GET
    @Path("/ping")
    @Timed
    @Operation(summary = "Health check", description = "Simple health check endpoint that returns 'pong'", tags = {"Utility"})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Health check response")
    public ApiResponse<String> ping() {
        return success("pong");
    }

    private <T> Response processRequest(
            final T request,
            final Function<T, ValidationResult> validator,
            final BiConsumer<T, ValidationResult> auditLogger,
            final Function<T, Object> successResponseBuilder) {
        if (CommonUtils.isInMaintenanceWindow(clusterStateDB.currentState().orElse(null))) {
            return maintenanceModeError();
        }
        val res = validator.apply(request);
        auditLogger.accept(request, res);
        if (res.getStatus().equals(ValidationStatus.SUCCESS)) {
            return ControllerUtils.ok(successResponseBuilder.apply(request));
        }
        return ControllerUtils.commandValidationFailure(res.getMessages());
    }

    private static Response maintenanceModeError() {
        return ControllerUtils.commandValidationFailure("Cluster is in maintenance mode");
    }

}
