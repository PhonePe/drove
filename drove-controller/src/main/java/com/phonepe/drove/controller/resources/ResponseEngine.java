package com.phonepe.drove.controller.resources;

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
import com.phonepe.drove.controller.statedb.InstanceInfoDB;
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
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 */
@Singleton
@Slf4j
public class ResponseEngine {

    private final ApplicationEngine engine;
    private final ApplicationStateDB applicationStateDB;
    private final InstanceInfoDB instanceInfoDB;
    private final ClusterResourcesDB clusterResourcesDB;
    private final ControllerCommunicator communicator;

    @Inject
    public ResponseEngine(
            ApplicationEngine engine,
            ApplicationStateDB applicationStateDB,
            InstanceInfoDB instanceInfoDB, ClusterResourcesDB clusterResourcesDB,
            ControllerCommunicator communicator) {
        this.engine = engine;
        this.applicationStateDB = applicationStateDB;
        this.instanceInfoDB = instanceInfoDB;
        this.clusterResourcesDB = clusterResourcesDB;
        this.communicator = communicator;
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
                .orElse(ApiResponse.failure("App " + appId + " not found"));
    }

    public ApiResponse<AppDetails> appDetails(String appId) {
        return applicationStateDB.application(appId)
                .map(this::toAppDetails)
                .map(ApiResponse::success)
                .orElse(ApiResponse.failure("No app found with id: " + appId));
    }

    public ApiResponse<List<InstanceInfo>> applicationInstances(final String appId, final Set<InstanceState> state) {
        val checkStates = null == state || state.isEmpty()
                          ? InstanceState.ACTIVE_STATES
                          : state;
        return ApiResponse.success(instanceInfoDB.activeInstances(appId, 0, Integer.MAX_VALUE)
                                           .stream()
                                           .filter(info -> checkStates.contains(info.getState()))
                                           .toList());
    }

    public ApiResponse<InstanceInfo> instanceDetails(final String appId, final String instanceId) {
        return instanceInfoDB.instance(appId, instanceId)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.failure("No such instance"));
    }

    public ApiResponse<List<InstanceInfo>> applicationOldInstances(final String appId) {
        return ApiResponse.success(instanceInfoDB.oldInstances(appId, 0, Integer.MAX_VALUE)
                                           .stream()
                                           .toList());
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
                        .toList());
    }

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

    private AppSummary toAppSummary(final ApplicationInfo info) {
        val spec = info.getSpec();
        val instances = info.getInstances();
        val healthyInstances = instanceInfoDB.instanceCount(info.getAppId(), InstanceState.HEALTHY);
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
                                                               executorData.getTags()));
                    }
                });
    }

    public ApiResponse<com.phonepe.drove.models.info.nodedata.ExecutorNodeData> executorDetails(String executorId) {
        return clusterResourcesDB.currentSnapshot(executorId)
                .map(ExecutorHostInfo::getNodeData)
                .map(ApiResponse::success)
                .orElse(ApiResponse.failure("No executor found with id: " + executorId));
    }

    public ApiResponse<List<ExposedAppInfo>> endpoints() {
        //TODO::HANDLE EXPOSURE MODE
        return ApiResponse.success(applicationStateDB.applications(0, Integer.MAX_VALUE)
                                           .stream()
                                           .filter(app -> engine.applicationState(app.getAppId())
                                                   .filter(s -> s.equals(ApplicationState.RUNNING)
                                                           || s.equals(ApplicationState.SCALING_REQUESTED)
                                                           || s.equals(ApplicationState.REPLACE_INSTANCES_REQUESTED))
                                                   .isPresent()) //Only running
                                           .filter(app -> app.getSpec().getExposureSpec() != null) //Has exposure spec
                                           .filter(app -> !app.getSpec()
                                                   .getExposedPorts()
                                                   .isEmpty()) //Has any exposed ports
                                           .sorted(Comparator.comparing(ApplicationInfo::getAppId)) //Reduce chaos
                                           .map(app -> {
                                               val spec = app.getSpec().getExposureSpec();
                                               return new ExposedAppInfo(app.getAppId(), spec.getVhost(),
                                                                         instanceInfoDB.healthyInstances(app.getAppId())
                                                                                 .stream()
                                                                                 .sorted(Comparator.comparing(
                                                                                         InstanceInfo::getCreated)) //Reduce chaos
                                                                                 .filter(instanceInfo -> instanceInfo.getLocalInfo()
                                                                                         .getPorts()
                                                                                         .containsKey(spec.getPortName())) //Has the specified port exposed
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
                return ApiResponse.success(null);
            }
            return ApiResponse.failure("Error sending remote message");
        }
        return ApiResponse.failure("No such executor");
    }

    public ApiResponse<Void> unblacklistExecutor(final String executorId) {
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
                return ApiResponse.success(null);
            }
            return ApiResponse.failure("Error sending remote message");
        }
        return ApiResponse.failure("No such executor");
    }
}
