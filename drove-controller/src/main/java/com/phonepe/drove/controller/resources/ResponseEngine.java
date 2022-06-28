package com.phonepe.drove.controller.resources;

import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.coverageutils.IgnoreInJacocoGeneratedReport;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.executor.BlacklistExecutorMessage;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.model.executor.UnBlacklistExecutorMessage;
import com.phonepe.drove.controller.engine.ApplicationEngine;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.ClusterStateDB;
import com.phonepe.drove.controller.statedb.InstanceInfoDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.api.*;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.application.requirements.ResourceRequirementVisitor;
import com.phonepe.drove.models.common.ClusterState;
import com.phonepe.drove.models.common.ClusterStateData;
import com.phonepe.drove.models.info.nodedata.ControllerNodeData;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.NodeDataVisitor;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

import static com.phonepe.drove.models.api.ApiResponse.failure;
import static com.phonepe.drove.models.api.ApiResponse.success;
import static com.phonepe.drove.models.instance.InstanceState.HEALTHY;

/**
 * Returns responses for use in apis
 */
@Singleton
@Slf4j
public class ResponseEngine {

    private static final Set<ApplicationState> EXPOSED_STATES = EnumSet.of(ApplicationState.RUNNING,
                                                                           ApplicationState.SCALING_REQUESTED,
                                                                           ApplicationState.REPLACE_INSTANCES_REQUESTED);

    private final ApplicationEngine engine;
    private final ApplicationStateDB applicationStateDB;
    private final InstanceInfoDB instanceInfoDB;
    private final ClusterStateDB clusterStateDB;
    private final ClusterResourcesDB clusterResourcesDB;
    private final ControllerCommunicator communicator;

    @Inject
    public ResponseEngine(
            ApplicationEngine engine,
            ApplicationStateDB applicationStateDB,
            InstanceInfoDB instanceInfoDB,
            ClusterStateDB clusterStateDB, ClusterResourcesDB clusterResourcesDB,
            ControllerCommunicator communicator) {
        this.engine = engine;
        this.applicationStateDB = applicationStateDB;
        this.instanceInfoDB = instanceInfoDB;
        this.clusterStateDB = clusterStateDB;
        this.clusterResourcesDB = clusterResourcesDB;
        this.communicator = communicator;
    }


    public ApiResponse<Map<String, AppSummary>> applications(final int from, final int size) {
        val apps = applicationStateDB.applications(from, size);
        val appIds = apps.stream().map(ApplicationInfo::getAppId).toList();
        val instanceCounts = instanceInfoDB.instanceCount(appIds, HEALTHY);
        return success(apps.stream()
                               .collect(Collectors.toUnmodifiableMap(ApplicationInfo::getAppId,
                                                                     info -> toAppSummary(info, instanceCounts))));
    }


    public ApiResponse<AppSummary> application(final String appId) {
        return applicationStateDB.application(appId)
                .map(appInfo -> success(toAppSummary(appInfo, instanceInfoDB.instanceCount(Set.of(appId), HEALTHY))))
                .orElse(failure("App " + appId + " not found"));
    }

    public ApiResponse<ApplicationSpec> applicationSpec(final String appId) {
        return applicationStateDB.application(appId)
                .map(appInfo -> success(appInfo.getSpec()))
                .orElse(failure("App " + appId + " not found"));
    }

    public ApiResponse<List<InstanceInfo>> applicationInstances(final String appId, final Set<InstanceState> state) {
        val checkStates = null == state || state.isEmpty()
                          ? InstanceState.ACTIVE_STATES
                          : state;
        return success(instanceInfoDB.activeInstances(appId, 0, Integer.MAX_VALUE)
                               .stream()
                               .filter(info -> checkStates.contains(info.getState()))
                               .toList());
    }

    public ApiResponse<InstanceInfo> instanceDetails(final String appId, final String instanceId) {
        return instanceInfoDB.instance(appId, instanceId)
                .map(ApiResponse::success)
                .orElseGet(() -> failure("No such instance"));
    }

    public ApiResponse<List<InstanceInfo>> applicationOldInstances(final String appId, int start, int length) {
        return success(instanceInfoDB.oldInstances(appId, start, length));
    }

    public ApiResponse<ClusterSummary> cluster() {
        var liveApps = 0;
        var allApps = 0;
        for (val appInfo : applicationStateDB.applications(0, Integer.MAX_VALUE)) {
            liveApps += ApplicationState.ACTIVE_APP_STATES.contains(engine.applicationState(appInfo.getAppId())
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
        return success(
                new ClusterSummary(
                        clusterStateDB.currentState()
                                .map(ClusterStateData::getState)
                                .orElse(ClusterState.NORMAL),
                        executors.size(),
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
        return success(
                clusterResourcesDB.currentSnapshot()
                        .stream()
                        .map(hostInfo -> toExecutorSummary(hostInfo).orElse(null))
                        .filter(Objects::nonNull)
                        .toList());
    }

    public ApiResponse<ExecutorNodeData> executorDetails(String executorId) {
        return clusterResourcesDB.currentSnapshot(executorId)
                .map(ExecutorHostInfo::getNodeData)
                .map(ApiResponse::success)
                .orElse(failure("No executor found with id: " + executorId));
    }

    public ApiResponse<List<ExposedAppInfo>> endpoints() {
        //TODO::HANDLE EXPOSURE MODE
        val apps = applicationStateDB.applications(0, Integer.MAX_VALUE)
                .stream()
                .filter(app -> engine.applicationState(app.getAppId())
                        .filter(EXPOSED_STATES::contains)
                        .isPresent()) //Only running
                .filter(app -> app.getSpec().getExposureSpec() != null && !app.getSpec()
                        .getExposedPorts()
                        .isEmpty()) //Has any exposed ports
                .sorted(Comparator.comparing(ApplicationInfo::getAppId)) //Reduce chaos by sorting so that order
                // remains same
                .toList();
        val appIds = apps.stream().map(ApplicationInfo::getAppId).toList();
        //Skip stale check if cluster is in maintenance mode
        val instances = instanceInfoDB.instances(appIds, Set.of(HEALTHY), isInMaintenanceWindow());
        return success(apps.stream()
                               .map(app -> {
                                   val spec = app.getSpec().getExposureSpec();
                                   return new ExposedAppInfo(app.getAppId(), spec.getVhost(),
                                                             instances.getOrDefault(app.getAppId(), List.of())
                                                                     .stream()
                                                                     .sorted(Comparator.comparing(
                                                                             InstanceInfo::getCreated)) //Reduce chaos
                                                                     .filter(instanceInfo -> instanceInfo.getLocalInfo()
                                                                             .getPorts()
                                                                             .containsKey(spec.getPortName())) //Has
                                                                     // the specified port exposed
                                                                     .map(instanceInfo -> new ExposedAppInfo.ExposedHost(
                                                                             instanceInfo.getLocalInfo()
                                                                                     .getHostname(),
                                                                             instanceInfo.getLocalInfo()
                                                                                     .getPorts()
                                                                                     .get(spec.getPortName())
                                                                                     .getHostPort(),
                                                                             instanceInfo.getLocalInfo()
                                                                                     .getPorts()
                                                                                     .get(spec.getPortName())
                                                                                     .getPortType()))
                                                                     .toList());
                               })
                               .toList());
    }


    public ApiResponse<Void> blacklistExecutor(final String executorId) {
        if (CommonUtils.isInMaintenanceWindow(clusterStateDB.currentState().orElse(null))) {
            return ApiResponse.failure("Cluster is in maintenance mode");
        }
        val executor = clusterResourcesDB.currentSnapshot(executorId).orElse(null);
        if (null != executor) {
            val msgResponse = communicator.send(
                    new BlacklistExecutorMessage(MessageHeader.controllerRequest(),
                                                 new ExecutorAddress(executor.getExecutorId(),
                                                                     executor.getNodeData().getHostname(),
                                                                     executor.getNodeData().getPort(),
                                                                     executor.getNodeData().getTransportType())));
            if (msgResponse.getStatus().equals(MessageDeliveryStatus.ACCEPTED)) {
                clusterResourcesDB.markBlacklisted(executorId);
                log.info("Executor {} has been marked as blacklisted. Moving running instances", executorId);
                engine.moveInstancesFromExecutor(executorId);
                return success(null);
            }
            return failure("Error sending remote message");
        }
        return failure("No such executor");
    }

    public ApiResponse<Void> unblacklistExecutor(final String executorId) {
        if (CommonUtils.isInMaintenanceWindow(clusterStateDB.currentState().orElse(null))) {
            return ApiResponse.failure("Cluster is in maintenance mode");
        }
        val executor = clusterResourcesDB.currentSnapshot(executorId).orElse(null);
        if (null != executor) {
            val msgResponse = communicator.send(
                    new UnBlacklistExecutorMessage(MessageHeader.controllerRequest(),
                                                   new ExecutorAddress(executor.getExecutorId(),
                                                                       executor.getNodeData().getHostname(),
                                                                       executor.getNodeData().getPort(),
                                                                       executor.getNodeData().getTransportType())));
            if (msgResponse.getStatus().equals(MessageDeliveryStatus.ACCEPTED)) {
                clusterResourcesDB.unmarkBlacklisted(executorId);
                log.debug("Executor {} marked unblacklisted.", executorId);
                return success(null);
            }
            return failure("Error sending remote message");
        }
        return failure("No such executor");
    }

    public ApiResponse<ClusterStateData> setClusterMaintenanceMode() {
        return clusterStateDB.setClusterState(ClusterState.MAINTENANCE)
                .map(ApiResponse::success)
                .orElse(failure("Could not change cluster state"));
    }

    public ApiResponse<ClusterStateData> unsetClusterMaintenanceMode() {
        return clusterStateDB.setClusterState(ClusterState.NORMAL)
                .map(ApiResponse::success)
                .orElse(failure("Could not change cluster state"));
    }

    @IgnoreInJacocoGeneratedReport
    private AppDetails toAppDetails(final ApplicationInfo info) {
        val spec = info.getSpec();
        val requiredInstances = info.getInstances();
        val instances = instanceInfoDB.activeInstances(info.getAppId(), 0, Integer.MAX_VALUE);
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

    private AppSummary toAppSummary(final ApplicationInfo info, Map<String, Long> instanceCounts) {
        val spec = info.getSpec();
        val instances = info.getInstances();
        val healthyInstances = instanceCounts.getOrDefault(info.getAppId(), 0L);
        val cpus = totalCPU(spec, instances);
        val memory = totalMemory(spec, instances);
        return new AppSummary(info.getAppId(),
                              spec.getName(),
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
                                                               hostInfo.getNodeData().getTransportType(),
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
                                                                       .sum(),
                                                               executorData.getTags(),
                                                               executorData.isBlacklisted()));
                    }
                });
    }

    private boolean isInMaintenanceWindow() {
        return CommonUtils.isInMaintenanceWindow(clusterStateDB.currentState().orElse(null));
    }
}
