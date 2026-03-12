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

import com.phonepe.drove.auth.model.DroveUserRole;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.controller.engine.LocalServiceLifecycleManagementEngine;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.internal.KnownInstancesData;
import com.phonepe.drove.models.internal.LocalServiceInstanceResources;
import com.phonepe.drove.models.localservice.ActivationState;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import lombok.val;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
    private final LocalServiceStateDB localServiceStateDB;
    private final LocalServiceLifecycleManagementEngine localServiceEngine;

    @Inject
    public InternalApis(
            ClusterResourcesDB resourcesDB,
            ApplicationStateDB applicationStateDB,
            TaskDB taskDB,
            LocalServiceStateDB localServiceStateDB,
            LocalServiceLifecycleManagementEngine localServiceEngine) {
        this.resourcesDB = resourcesDB;
        this.applicationStateDB = applicationStateDB;
        this.taskDB = taskDB;
        this.localServiceStateDB = localServiceStateDB;
        this.localServiceEngine = localServiceEngine;
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

    @GET
    @Path("/resources/reserved")
    public ApiResponse<LocalServiceInstanceResources> reservedResources() {
        var requiredCPU = 0L;
        var requiredMemory = 0L;
        val requiredApps = new HashMap<String, Integer>();
        for(val localServiceInfo : localServiceStateDB.services(0, Integer.MAX_VALUE)) {
            val currState = localServiceEngine.currentState(localServiceInfo.getServiceId())
                    .orElse(LocalServiceState.DESTROYED);
            val spec = localServiceInfo.getSpec();
            if(LocalServiceState.RESOURCE_USING_STATES.contains(currState)) {
                val instancesPerNode = localServiceInfo.getInstancesPerHost();
                requiredCPU += ControllerUtils.totalCPU(spec, instancesPerNode);
                requiredMemory+= ControllerUtils.totalMemory(spec, instancesPerNode);
                requiredApps.put(localServiceInfo.getServiceId(), instancesPerNode);
            }
        }
        return ApiResponse.success(new LocalServiceInstanceResources(requiredCPU, requiredMemory, requiredApps));
    }

    private ApiResponse<KnownInstancesData> knownInstances(
            String executorId,
            Supplier<Optional<ExecutorHostInfo>> hostInfoGenerator) {
        return hostInfoGenerator.get()
                .map(ExecutorHostInfo::getNodeData)
                .map(this::buildKnownInstanceData)
                .map(ApiResponse::success)
                .orElse(ApiResponse.failure("No data found for executor " + executorId));
    }

    private KnownInstancesData buildKnownInstanceData(ExecutorNodeData executorNodeData) {
        val appInstances = new HashSet<String>();
        val staleAppInstances = new HashSet<String>();
        val taskInstances = new HashSet<String>();
        val staleTaskInstances = new HashSet<String>();
        val lsInstances = new HashSet<String>();
        val staleLSInstances = new HashSet<String>();
        for (val instanceInfo : Objects.requireNonNullElse(executorNodeData.getInstances(),
                                                           List.<InstanceInfo>of())) {
            if (applicationStateDB.application(instanceInfo.getAppId())
                    .filter(applicationInfo -> applicationInfo.getInstances() > 0)
                    .isPresent()) {
                appInstances.add(instanceInfo.getInstanceId());
            }
            else {
                staleAppInstances.add(instanceInfo.getInstanceId());
            }
        }
        for (val taskInfo : Objects.requireNonNullElse(executorNodeData.getTasks(), List.<TaskInfo>of())) {
            if (taskDB.task(taskInfo.getSourceAppName(), taskInfo.getTaskId()).isPresent()) {
                taskInstances.add(taskInfo.getInstanceId());
            }
            else {
                staleTaskInstances.add(taskInfo.getInstanceId());
            }
        }
        val instancesByService = Objects.requireNonNullElse(executorNodeData.getServiceInstances(),
                                                            List.<LocalServiceInstanceInfo>of())
                .stream()
                .collect(Collectors.groupingBy(LocalServiceInstanceInfo::getServiceId,
                                               Collectors.mapping(LocalServiceInstanceInfo::getInstanceId,
                                                                  Collectors.toUnmodifiableList())));
        instancesByService.forEach((serviceId, instances) -> populateServiceInstances(serviceId, instances, lsInstances, staleLSInstances));

        return new KnownInstancesData(appInstances,
                                      staleAppInstances,
                                      taskInstances,
                                      staleTaskInstances,
                                      lsInstances,
                                      staleLSInstances);
    }

    private void populateServiceInstances(
            String serviceId,
            List<String> instances,
            Set<String> lsInstances,
            Set<String> staleLSInstances) {
        val service = localServiceStateDB.service(serviceId);
        if (service.isPresent()
                && (service.get().getActivationState() == ActivationState.ACTIVE
                || service.get().getActivationState() == ActivationState.CONFIG_TESTING)) {
            val extraInstances = service
                    .filter(serviceInfo -> serviceInfo.getInstancesPerHost() < instances.size())
                    .map(serviceInfo -> CommonUtils.sublist(instances,
                                                            serviceInfo.getInstancesPerHost(),
                                                            instances.size()))
                    .orElse(List.of());
            if (extraInstances.isEmpty()) {
                lsInstances.addAll(instances);
            }
            else {
                lsInstances.addAll(CommonUtils.sublist(instances,
                                                       0,
                                                       service.get().getInstancesPerHost()));
                staleLSInstances.addAll(extraInstances);
            }
        }
        else {
            staleLSInstances.addAll(instances);
        }
    }
}
