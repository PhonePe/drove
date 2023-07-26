package com.phonepe.drove.controller.resources;

import com.phonepe.drove.auth.model.DroveUserRole;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.internal.KnownInstancesData;
import lombok.val;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 *
 */
@Path("/v1/internal/cluster")
@RolesAllowed(DroveUserRole.Values.DROVE_CLUSTER_NODE_ROLE)
@Produces(MediaType.APPLICATION_JSON)
public class InternalApis {
    private final ClusterResourcesDB resourcesDB;
    private final ApplicationStateDB applicationStateDB;
    private final TaskDB taskDB;

    @Inject
    public InternalApis(ClusterResourcesDB resourcesDB, ApplicationStateDB applicationStateDB, TaskDB taskDB) {
        this.resourcesDB = resourcesDB;
        this.applicationStateDB = applicationStateDB;
        this.taskDB = taskDB;
    }

    /**
     * @deprecated Please use one of the other apis
     * @param executorId Executor if for which snapshot is needed
     * @return Snapshot
     */
    @GET
    @Path("/executors/{executorId}/instances")
    @Deprecated
    public ApiResponse<KnownInstancesData> validInstances(@PathParam("executorId") final String executorId) {
        return knownInstances(executorId, () -> resourcesDB.lastKnownSnapshot(executorId));
    }

    @GET
    @Path("/executors/{executorId}/instances/last")
    public ApiResponse<KnownInstancesData> lastKnownInstances(@PathParam("executorId") final String executorId) {
        return knownInstances(executorId, () -> resourcesDB.lastKnownSnapshot(executorId));
    }

    @GET
    @Path("/executors/{executorId}/instances/current")
    public ApiResponse<KnownInstancesData> currentKnownInstances(@PathParam("executorId") final String executorId) {
        return knownInstances(executorId, () -> resourcesDB.currentSnapshot(executorId));
    }

    private ApiResponse<KnownInstancesData> knownInstances(String executorId,
                                                           Supplier<Optional<ExecutorHostInfo>> hostInfoGenerator) {
        return hostInfoGenerator.get()
                .map(ExecutorHostInfo::getNodeData)
                .map(executorNodeData -> {
                    val appInstances = new HashSet<String>();
                    val staleAppInstances = new HashSet<String>();
                    val taskInstances = new HashSet<String>();
                    val staleTaskInstances = new HashSet<String>();
                    for(val instanceInfo : Objects.requireNonNull(executorNodeData.getInstances())) {
                        if(applicationStateDB.application(instanceInfo.getAppId()).isPresent()) {
                            appInstances.add(instanceInfo.getInstanceId());
                        }
                        else {
                            staleAppInstances.add(instanceInfo.getInstanceId());
                        }
                    }
                    for(val taskInfo : Objects.requireNonNull(executorNodeData.getTasks())) {
                        if(taskDB.task(taskInfo.getSourceAppName(), taskInfo.getTaskId()).isPresent()) {
                            taskInstances.add(taskInfo.getInstanceId());
                        }
                        else {
                            staleTaskInstances.add(taskInfo.getInstanceId());
                        }
                    }
                    return new KnownInstancesData(appInstances, staleAppInstances, taskInstances, staleTaskInstances);
                })
                .map(ApiResponse::success)
                .orElse(ApiResponse.failure("No data found for executor " + executorId));
    }
}
