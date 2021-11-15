package com.phonepe.drove.controller.resources;

import com.phonepe.drove.common.discovery.nodedata.ControllerNodeData;
import com.phonepe.drove.common.discovery.nodedata.ExecutorNodeData;
import com.phonepe.drove.common.discovery.nodedata.NodeDataVisitor;
import com.phonepe.drove.controller.engine.ApplicationEngine;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.api.ApiErrorCode;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.api.AppDetails;
import com.phonepe.drove.models.api.ExecutorSummary;
import com.phonepe.drove.models.application.ApplicationInfo;
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
import java.util.stream.Collectors;

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
    private final ApplicationStateDB applicationStateDB;
    private final ClusterResourcesDB clusterResourcesDB;

    @Inject
    public Apis(
            ApplicationEngine engine,
            ApplicationStateDB applicationStateDB,
            ClusterResourcesDB clusterResourcesDB) {
        this.engine = engine;
        this.applicationStateDB = applicationStateDB;
        this.clusterResourcesDB = clusterResourcesDB;
    }

    @POST
    @Path("/operations")
    public ApiResponse<Map<String, String>> acceptOperation(@NotNull @Valid final ApplicationOperation operation) {
        engine.handleOperation(operation);
        return ApiResponse.success(Collections.singletonMap("appId", ControllerUtils.appId(operation)));
    }

    @GET
    @Path("/applications")
    public ApiResponse<Map<String, AppDetails>> applications(
            @QueryParam("from") @DefaultValue("0") @Min(0) @Max(1024) final int from,
            @QueryParam("size") @DefaultValue("1024") @Min(0) @Max(1024) final int size) {
        return ApiResponse.success(applicationStateDB.applications(from, size)
                                           .stream()
                                           .collect(Collectors.toUnmodifiableMap(ApplicationInfo::getAppId,
                                                                                 this::toDetails)));
    }

    @GET
    @Path("/applications/{id}")
    public ApiResponse<AppDetails> application(@PathParam("id") @NotEmpty final String appId) {
        return applicationStateDB.application(appId)
                .map(appInfo -> ApiResponse.success(toDetails(appInfo)))
                .orElse(new ApiResponse<>(ApiErrorCode.FAILED, null, "App " + appId + " not found"));
    }

    @GET
    @Path("/applications/{id}/instances")
    public ApiResponse<List<InstanceInfo>> applicationInstances(
            @PathParam("id") @NotEmpty final String appId,
            @QueryParam("state") final Set<InstanceState> state) {

        val checkStates = null == state || state.isEmpty()
                          ? ACTIVE_STATES
                          : state;
        return ApiResponse.success(applicationStateDB.instances(appId, 0, Integer.MAX_VALUE)
                                           .stream()
                                           .filter(info -> checkStates.contains(info.getState()))
                                           .collect(Collectors.toUnmodifiableList()));
    }

    @GET
    @Path("/cluster/executors")
    public ApiResponse<List<ExecutorSummary>> nodes() {
        return ApiResponse.success(
                clusterResourcesDB.currentSnapshot()
                        .stream()
                        .map(hostInfo -> toSummary(hostInfo).orElse(null))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toUnmodifiableList()));
    }

    private AppDetails toDetails(final ApplicationInfo info) {
        return new AppDetails(info.getSpec(),
                              info.getInstances(),
                              engine.applicationState(info.getAppId()).orElse(null),
                              info.getCreated(),
                              info.getUpdated());

    }

    private Optional<ExecutorSummary> toSummary(final ExecutorHostInfo hostInfo) {
        return hostInfo.getNodeData()
                .accept(new NodeDataVisitor<Optional<ExecutorSummary>>() {
                    @Override
                    public Optional<ExecutorSummary> visit(ControllerNodeData controllerData) {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<ExecutorSummary> visit(ExecutorNodeData executorData) {
                        return Optional.of(new ExecutorSummary(hostInfo.getExecutorId(),
                                                               hostInfo.getNodeData().getHostname(),
                                                               hostInfo.getNodeData().getPort(),
                                                               executorData.getState()
                                                                       .getCpus()
                                                                       .getFreeCores()
                                                                       .values()
                                                                       .stream()
                                                                       .mapToInt(Set::size)
                                                                       .sum(),
                                                               executorData.getState()
                                                                       .getCpus()
                                                                       .getUsedCores()
                                                                       .values()
                                                                       .stream()
                                                                       .mapToInt(Set::size)
                                                                       .sum(),
                                                               executorData.getState()
                                                                       .getMemory()
                                                                       .getFreeMemory()
                                                                       .values()
                                                                       .stream()
                                                                       .mapToLong(v -> v)
                                                                       .sum(),
                                                               executorData.getState()
                                                                       .getMemory()
                                                                       .getUsedMemory()
                                                                       .values()
                                                                       .stream()
                                                                       .mapToLong(v -> v)
                                                                       .sum()));
                    }
                });
    }
}
