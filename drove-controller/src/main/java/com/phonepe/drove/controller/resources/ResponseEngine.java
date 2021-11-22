package com.phonepe.drove.controller.resources;

import com.phonepe.drove.controller.engine.ApplicationEngine;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.api.*;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.application.requirements.ResourceRequirementVisitor;
import com.phonepe.drove.models.info.nodedata.ControllerNodeData;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.NodeDataVisitor;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 */
@Singleton
public class ResponseEngine {
    private static final EnumSet<ApplicationState> ACTIVE_APP_STATES = EnumSet.of(ApplicationState.RUNNING,
                                                                                  ApplicationState.OUTAGE_DETECTED,
                                                                                  ApplicationState.SCALING_REQUESTED,
                                                                                  ApplicationState.STOP_INSTANCES_REQUESTED,
                                                                                  ApplicationState.RESTART_REQUESTED,
                                                                                  ApplicationState.DESTROY_REQUESTED);

    private static final EnumSet<InstanceState> ACTIVE_INSTANCE_STATES = EnumSet.of(InstanceState.PENDING,
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
    public ResponseEngine(
            ApplicationEngine engine,
            ApplicationStateDB applicationStateDB,
            ClusterResourcesDB clusterResourcesDB) {
        this.engine = engine;
        this.applicationStateDB = applicationStateDB;
        this.clusterResourcesDB = clusterResourcesDB;
    }

    public ApiResponse<Map<String, AppSummary>> applications(final int from, final int size) {
        return ApiResponse.success(applicationStateDB.applications(from, size)
                                           .stream()
                                           .collect(Collectors.toUnmodifiableMap(ApplicationInfo::getAppId,
                                                                                 this::toAppSummary)));
    }


    public ApiResponse<AppSummary> application(final String appId) {
        return applicationStateDB.application(appId)
                .map(appInfo -> ApiResponse.success(toAppSummary(appInfo)))
                .orElse(new ApiResponse<>(ApiErrorCode.FAILED, null, "App " + appId + " not found"));
    }

    public ApiResponse<AppDetails> appDetails(String appId) {
        return applicationStateDB.application(appId)
                .map(this::toAppDetails)
                .map(ApiResponse::success)
                .orElse(new ApiResponse<>(ApiErrorCode.FAILED, null, "No app found with id: " + appId));
    }

    public ApiResponse<List<InstanceInfo>> applicationInstances(
            final String appId, final Set<InstanceState> state) {

        val checkStates = null == state || state.isEmpty()
                          ? ACTIVE_INSTANCE_STATES
                          : state;
        return ApiResponse.success(applicationStateDB.instances(appId, 0, Integer.MAX_VALUE)
                                           .stream()
                                           .filter(info -> checkStates.contains(info.getState()))
                                           .collect(Collectors.toUnmodifiableList()));
    }

    public ApiResponse<ClusterSummary> cluster() {
        var liveApps = 0;
        var allApps = 0;
        for (val appInfo : applicationStateDB.applications(0, Integer.MAX_VALUE)) {
            liveApps += ACTIVE_APP_STATES.contains(engine.applicationState(appInfo.getAppId())
                                                           .orElse(ApplicationState.FAILED))
                        ? 1
                        : 0;
            allApps++;
        }
        var freeCores = 0;
        var usedCores = 0;
        var freeMemory = 0L;
        var usedMemory = 0L;
        val executors = clusterResourcesDB.currentSnapshot();
        for (val executor : executors) {
            usedCores += ControllerUtils.usedCores(executor);
            freeCores += ControllerUtils.freeCores(executor);
            freeMemory += ControllerUtils.freeMemory(executor);
            usedMemory += ControllerUtils.usedMemory(executor);
        }
        return ApiResponse.success(new ClusterSummary(executors.size(),
                                                      allApps,
                                                      liveApps,
                                                      freeCores,
                                                      usedCores,
                                                      freeCores + usedCores,
                                                      freeMemory,
                                                      usedMemory,
                                                      freeMemory + usedMemory));
    }

    public ApiResponse<List<ExecutorSummary>> nodes() {
        return ApiResponse.success(
                clusterResourcesDB.currentSnapshot()
                        .stream()
                        .map(hostInfo -> toExecutorSummary(hostInfo).orElse(null))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toUnmodifiableList()));
    }

    private AppDetails toAppDetails(final ApplicationInfo info) {
        val spec = info.getSpec();
        val requiredInstances = info.getInstances();
        val instances = applicationStateDB.instances(info.getAppId(), 0, Integer.MAX_VALUE);
        val cpus = totalCPU(spec, requiredInstances);
        val memory = totalMemory(spec, requiredInstances);
        return new AppDetails(info.getAppId(),
                              spec,
                              requiredInstances,
                              instances.stream()
                                      .filter(instanceInfo -> instanceInfo.getState().equals(InstanceState.HEALTHY))
                                      .count(),
                              cpus,
                              memory,
                              instances,
                              engine.applicationState(info.getAppId()).orElse(null),
                              info.getCreated(),
                              info.getUpdated());

    }

    private AppSummary toAppSummary(final ApplicationInfo info) {
        val spec = info.getSpec();
        val instances = info.getInstances();
        val healthyInstances = applicationStateDB.instanceCount(info.getAppId(), InstanceState.HEALTHY);
        val cpus = totalCPU(spec, instances);
        val memory = totalMemory(spec, instances);
        return new AppSummary(info.getAppId(),
                              spec,
                              instances,
                              healthyInstances,
                              cpus,
                              memory,
                              engine.applicationState(info.getAppId()).orElse(null),
                              info.getCreated(),
                              info.getUpdated());

    }

    private long totalMemory(ApplicationSpec spec, long instances) {
        return instances * spec.getResources()
                .stream()
                .mapToLong(r -> r.accept(new ResourceRequirementVisitor<>() {
                    @Override
                    public Long visit(CPURequirement cpuRequirement) {
                        return 0L;
                    }

                    @Override
                    public Long visit(MemoryRequirement memoryRequirement) {
                        return memoryRequirement.getSizeInMB();
                    }
                }))
                .sum();
    }

    private long totalCPU(ApplicationSpec spec, long instances) {
        return instances * spec.getResources().stream().mapToLong(r -> r.accept(new ResourceRequirementVisitor<>() {
                    @Override
                    public Long visit(CPURequirement cpuRequirement) {
                        return cpuRequirement.getCount();
                    }

                    @Override
                    public Long visit(MemoryRequirement memoryRequirement) {
                        return 0L;
                    }
                }))
                .sum();
    }

    private Optional<ExecutorSummary> toExecutorSummary(final ExecutorHostInfo hostInfo) {
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

    public ApiResponse<com.phonepe.drove.models.info.nodedata.ExecutorNodeData> executorDetails(String executorId) {
        return clusterResourcesDB.currentSnapshot(executorId)
                .map(ExecutorHostInfo::getNodeData)
                .map(ApiResponse::success)
                .orElse(new ApiResponse<>(ApiErrorCode.FAILED, null, "No executor found with id: " + executorId));
    }
}
