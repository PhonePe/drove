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

import com.google.common.collect.Sets;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.coverageutils.IgnoreInJacocoGeneratedReport;
import com.phonepe.drove.common.discovery.leadership.LeadershipObserver;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.executor.BlacklistExecutorMessage;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.model.executor.UnBlacklistExecutorMessage;
import com.phonepe.drove.controller.engine.ApplicationLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.LocalServiceLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.TaskEngine;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.event.EventStore;
import com.phonepe.drove.controller.managed.BlacklistingAppMovementManager;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.statedb.*;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.controller.utils.EventUtils;
import com.phonepe.drove.models.api.*;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.common.ClusterState;
import com.phonepe.drove.models.common.ClusterStateData;
import com.phonepe.drove.models.events.DroveEvent;
import com.phonepe.drove.models.events.events.DroveClusterMaintenanceModeRemovedEvent;
import com.phonepe.drove.models.events.events.DroveClusterMaintenanceModeSetEvent;
import com.phonepe.drove.models.events.events.DroveExecutorBlacklistedEvent;
import com.phonepe.drove.models.events.events.DroveExecutorUnblacklistedEvent;
import com.phonepe.drove.models.info.nodedata.ControllerNodeData;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.info.nodedata.NodeDataVisitor;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import com.phonepe.drove.models.localservice.LocalServiceSpec;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskState;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

import static com.phonepe.drove.controller.utils.ControllerUtils.totalCPU;
import static com.phonepe.drove.controller.utils.ControllerUtils.totalMemory;
import static com.phonepe.drove.controller.utils.EventUtils.executorMetadata;
import static com.phonepe.drove.models.api.ApiResponse.failure;
import static com.phonepe.drove.models.api.ApiResponse.success;
import static com.phonepe.drove.models.instance.InstanceState.HEALTHY;

/**
 * Returns responses for use in apis
 */
@Singleton
@Slf4j
public class ResponseEngine {

    private final LeadershipObserver leadershipObserver;
    private final ApplicationLifecycleManagementEngine applicationEngine;
    private final ApplicationStateDB applicationStateDB;
    private final ApplicationInstanceInfoDB instanceInfoDB;
    private final TaskDB taskDB;
    private final TaskEngine taskEngine;
    private final LocalServiceLifecycleManagementEngine localServiceEngine;
    private final LocalServiceStateDB localServiceStateDB;
    private final ClusterStateDB clusterStateDB;
    private final ClusterResourcesDB clusterResourcesDB;
    private final EventStore eventStore;
    private final ControllerCommunicator communicator;
    private final DroveEventBus eventBus;
    private final BlacklistingAppMovementManager blacklistingAppMovementManager;

    @Inject
    public ResponseEngine(
            LeadershipObserver leadershipObserver,
            ApplicationLifecycleManagementEngine applicationEngine,
            ApplicationStateDB applicationStateDB,
            ApplicationInstanceInfoDB instanceInfoDB,
            TaskDB taskDB, TaskEngine taskEngine,
            LocalServiceLifecycleManagementEngine localServiceEngine,
            LocalServiceStateDB localServiceStateDB,
            ClusterStateDB clusterStateDB,
            ClusterResourcesDB clusterResourcesDB,
            EventStore eventStore,
            ControllerCommunicator communicator,
            DroveEventBus eventBus,
            BlacklistingAppMovementManager blacklistingAppMovementManager) {
        this.leadershipObserver = leadershipObserver;
        this.applicationEngine = applicationEngine;
        this.applicationStateDB = applicationStateDB;
        this.instanceInfoDB = instanceInfoDB;
        this.taskDB = taskDB;
        this.taskEngine = taskEngine;
        this.localServiceEngine = localServiceEngine;
        this.localServiceStateDB = localServiceStateDB;
        this.clusterStateDB = clusterStateDB;
        this.clusterResourcesDB = clusterResourcesDB;
        this.eventStore = eventStore;
        this.communicator = communicator;
        this.eventBus = eventBus;
        this.blacklistingAppMovementManager = blacklistingAppMovementManager;
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
                .orElse(noAppFoundFailure(appId));
    }

    public ApiResponse<ApplicationSpec> applicationSpec(final String appId) {
        return applicationStateDB.application(appId)
                .map(appInfo -> success(appInfo.getSpec()))
                .orElse(noAppFoundFailure(appId));
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
                .orElseGet(() -> failure("No such application instance " + appId + "/" + instanceId));
    }

    public ApiResponse<List<InstanceInfo>> applicationOldInstances(final String appId, int start, int length) {
        return success(instanceInfoDB.oldInstances(appId, start, length));
    }

    public ApiResponse<TaskInfo> taskDetails(final String sourceAppName, final String taskId) {
        return taskDB.task(sourceAppName, taskId)
                .map(ApiResponse::success)
                .orElseGet(() -> failure("No such task instance " + sourceAppName + "/" + taskId));
    }

    public ApiResponse<Map<String, Boolean>> taskDelete(final String sourceAppName, final String taskId) {
        return taskDB.deleteTask(sourceAppName, taskId)
               ? ApiResponse.success(Map.of("deleted", true))
               : ApiResponse.failure(Map.of("deleted", false), "Could not delete " + sourceAppName + "/" + taskId);
    }

    public ApiResponse<Map<String, LocalServiceSummary>> localServices(final int from, final int size) {
        val services = localServiceStateDB.services(from, size);
        return success(services.stream()
                               .collect(Collectors.toUnmodifiableMap(LocalServiceInfo::getServiceId,
                                                                     info -> toLocalServiceSummary(info,
                                                                                                   instancesForService(
                                                                                                           info.getServiceId())))));
    }

    public ApiResponse<LocalServiceSummary> localService(final String serviceId) {
        return localServiceStateDB.service(serviceId)
                .map(appInfo -> success(toLocalServiceSummary(
                        appInfo,
                        instancesForService(serviceId))))
                .orElse(localServiceNotFoundFailure(serviceId));
    }


    public ApiResponse<LocalServiceSpec> localServiceSpec(final String serviceId) {
        return localServiceStateDB.service(serviceId)
                .map(info -> success(info.getSpec()))
                .orElse(localServiceNotFoundFailure(serviceId));
    }

    public ApiResponse<List<LocalServiceInstanceInfo>> localServiceInstances(
            final String serviceId,
            final Set<LocalServiceInstanceState> state) {
        val checkStates = null == state || state.isEmpty()
                          ? LocalServiceInstanceState.ACTIVE_STATES
                          : state;
        return success(localServiceStateDB.instances(serviceId, LocalServiceInstanceState.ACTIVE_STATES, false)
                               .stream()
                               .filter(info -> checkStates.contains(info.getState()))
                               .toList());
    }

    public ApiResponse<LocalServiceInstanceInfo> localServiceInstanceDetails(
            final String serviceId,
            final String instanceId) {
        return localServiceStateDB.instance(serviceId, instanceId)
                .map(ApiResponse::success)
                .orElseGet(() -> failure("No such local service instance " + serviceId + "/" + instanceId));
    }

    public ApiResponse<List<LocalServiceInstanceInfo>> localServiceOldInstances(final String serviceId) {
        return success(localServiceStateDB.oldInstances(serviceId));
    }

    public ApiResponse<ClusterSummary> cluster() {
        var liveApps = 0;
        var allApps = 0;
        for (val appInfo : applicationStateDB.applications(0, Integer.MAX_VALUE)) {
            liveApps += ApplicationState.ACTIVE_APP_STATES.contains(applicationEngine.currentState(appInfo.getAppId())
                                                                            .orElse(ApplicationState.FAILED))
                        ? 1
                        : 0;
            allApps++;
        }
        var liveTasks = 0;
        for (val taskInfo : taskEngine.activeTasks(EnumSet.allOf(TaskState.class))) {
            liveTasks += TaskState.ACTIVE_STATES.contains(taskInfo.getState())
                         ? 1
                         : 0;
        }
        var liveLocalServices = 0;
        var allLocalServices = 0;
        for (val localServiceInfo : localServiceStateDB.services(0, Integer.MAX_VALUE)) {
            liveLocalServices += LocalServiceState.RESOURCE_USING_STATES
                                         .contains(localServiceEngine.currentState(localServiceInfo.getServiceId())
                                                           .orElse(LocalServiceState.DESTROYED))
                                 ? 1
                                 : 0;
            allLocalServices++;
        }
        val resourceSummary = ControllerUtils.summarizeResources(clusterResourcesDB.currentSnapshot(true));

        return success(
                new ClusterSummary(
                        leadershipObserver.leader()
                                .map(node -> node.getHostname() + ":" + node.getPort())
                                .orElse("Leader election underway"),
                        clusterStateDB.currentState()
                                .map(ClusterStateData::getState)
                                .orElse(ClusterState.NORMAL),
                        resourceSummary.getNumExecutors(),
                        allApps,
                        liveApps,
                        liveTasks,
                        allLocalServices,
                        liveLocalServices,
                        resourceSummary.getFreeCores(),
                        resourceSummary.getUsedCores(),
                        resourceSummary.getTotalCores(),
                        resourceSummary.getFreeMemory(),
                        resourceSummary.getUsedMemory(),
                        resourceSummary.getTotalMemory()));
    }

    public ApiResponse<List<ExecutorSummary>> nodes() {
        val executors = new ArrayList<ExecutorSummary>();
        clusterResourcesDB.currentSnapshot(false) //Show everything
                .stream()
                .map(hostInfo -> toExecutorSummary(hostInfo, false).orElse(null))
                .filter(Objects::nonNull)
                .forEach(executors::add);
        clusterResourcesDB.lastKnownSnapshots()
                .stream()
                .map(hostInfo -> toExecutorSummary(hostInfo, true).orElse(null))
                .filter(Objects::nonNull)
                .forEach(executors::add);
        return success(executors);
    }

    public ApiResponse<ExecutorNodeData> executorDetails(String executorId) {
        return clusterResourcesDB.currentSnapshot(executorId)
                .map(ExecutorHostInfo::getNodeData)
                .map(ApiResponse::success)
                .orElse(failure("No executor found with id: " + executorId));
    }

    public ApiResponse<List<ExposedAppInfo>> endpoints(Set<String> appNames) {
        //TODO::HANDLE EXPOSURE MODE
        val apps = applicationStateDB.applications(0, Integer.MAX_VALUE)
                .stream()
                .filter(app -> applicationEngine.currentState(app.getAppId())
                        .filter(ApplicationState.ACTIVE_APP_STATES::contains)
                        .isPresent()) //Only running
                .filter(app -> app.getSpec().getExposureSpec() != null && !app.getSpec()
                        .getExposedPorts()
                        .isEmpty()) //Has any exposed ports
                .filter(app -> appNames == null || appNames.isEmpty() || appNames.contains(app.getSpec().getName()))
                .sorted(Comparator.comparing(ApplicationInfo::getAppId)) //Reduce chaos by sorting so that order
                // remains same
                .toList();
        val appIds = apps.stream().map(ApplicationInfo::getAppId).toList();
        //Skip stale check if cluster is in maintenance mode
        val instances = instanceInfoDB.instances(appIds, Set.of(HEALTHY), isInMaintenanceWindow());
        return success(apps.stream()
                               .map(app -> {
                                   val spec = app.getSpec().getExposureSpec();
                                   return new ExposedAppInfo(app.getSpec().getName(),
                                                             app.getAppId(),
                                                             spec.getVhost(),
                                                             app.getSpec().getTags(),
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

    public ApiResponse<Map<String, Set<String>>> blacklistExecutors(final Set<String> executorIds) {
        if (CommonUtils.isInMaintenanceWindow(clusterStateDB.currentState().orElse(null))) {
            return ApiResponse.failure("Cluster is in maintenance mode");
        }
        val successfullyBlacklisted = blacklistExecutorsInternal(executorIds);
        return success(Map.of("successful", successfullyBlacklisted,
                              "failed", Sets.difference(executorIds, successfullyBlacklisted)));
    }

    public ApiResponse<Map<String, Set<String>>> unblacklistExecutors(final Set<String> executorIds) {
        if (CommonUtils.isInMaintenanceWindow(clusterStateDB.currentState().orElse(null))) {
            return ApiResponse.failure("Cluster is in maintenance mode");
        }
        val successfullyUnblacklisted = executorIds.stream()
                .filter(executorId -> {
                    val executor = clusterResourcesDB.currentSnapshot(executorId).orElse(null);
                    if (null != executor) {
                        val msgResponse = communicator.send(
                                new UnBlacklistExecutorMessage(MessageHeader.controllerRequest(),
                                                               new ExecutorAddress(executor.getExecutorId(),
                                                                                   executor.getNodeData().getHostname(),
                                                                                   executor.getNodeData().getPort(),
                                                                                   executor.getNodeData()
                                                                                           .getTransportType())));
                        if (msgResponse.getStatus().equals(MessageDeliveryStatus.ACCEPTED)) {
                            clusterResourcesDB.unmarkBlacklisted(executorId);
                            log.debug("Executor {} marked unblacklisted.", executorId);
                            eventBus.publish(new DroveExecutorUnblacklistedEvent(executorMetadata(executor.getNodeData())));
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toUnmodifiableSet());
        return success(Map.of("successful", successfullyUnblacklisted,
                              "failed", Sets.difference(executorIds, successfullyUnblacklisted)));

    }

    public ApiResponse<ClusterStateData> setClusterMaintenanceMode() {
        return clusterStateDB.setClusterState(ClusterState.MAINTENANCE)
                .map(data -> {
                    eventBus.publish(new DroveClusterMaintenanceModeSetEvent(EventUtils.controllerMetadata()));
                    return ApiResponse.success(data);
                })
                .orElse(failure("Could not change cluster state"));
    }

    public ApiResponse<ClusterStateData> unsetClusterMaintenanceMode() {
        return clusterStateDB.setClusterState(ClusterState.NORMAL)
                .map(data -> {
                    eventBus.publish(new DroveClusterMaintenanceModeRemovedEvent(EventUtils.controllerMetadata()));
                    return ApiResponse.success(data);
                })
                .orElse(failure("Could not change cluster state"));
    }

    @SuppressWarnings("rawtypes")
    public ApiResponse<List<DroveEvent>> events(long lastSyncTime, int size) {
        return ApiResponse.success(eventStore.latest(lastSyncTime, size).getEvents());
    }

    public ApiResponse<DroveEventsList> eventList(long lastSyncTime, int size) {
        return ApiResponse.success(eventStore.latest(lastSyncTime, size));
    }

    public ApiResponse<DroveEventsSummary> summarize(long lastSyncTime) {
        return ApiResponse.success(eventStore.summarize(lastSyncTime));
    }

    private static <T> ApiResponse<T> noAppFoundFailure(String appId) {
        return failure("App " + appId + " not found");
    }

    private static <T> ApiResponse<T> localServiceNotFoundFailure(String serviceId) {
        return failure("Local service " + serviceId + " not found");
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
                              applicationEngine.currentState(info.getAppId()).orElse(null),
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
                              spec.getTags(),
                              applicationEngine.currentState(info.getAppId()).orElse(null),
                              info.getCreated(),
                              info.getUpdated());

    }

    private LocalServiceSummary toLocalServiceSummary(
            final LocalServiceInfo info,
            Map<String, List<LocalServiceInstanceInfo>> instancesPerExecutor) {
        val spec = info.getSpec();
        val knownInstances = instancesPerExecutor.values()
                .stream()
                .flatMap(List::stream)
                .filter(instanceInfo -> LocalServiceInstanceState.ACTIVE_STATES.contains(instanceInfo.getState()))
                .count();
        val healthyInstances = instancesPerExecutor.values()
                .stream()
                .flatMap(List::stream)
                .filter(instanceInfo -> instanceInfo.getState().equals(LocalServiceInstanceState.HEALTHY))
                .count();
        val cpus = totalCPU(spec, knownInstances);
        val memory = totalMemory(spec, knownInstances);
        return new LocalServiceSummary(info.getServiceId(),
                                       spec.getName(),
                                       info.getInstancesPerHost(),
                                       healthyInstances,
                                       cpus,
                                       memory,
                                       spec.getTags(),
                                       info.getActivationState(),
                                       localServiceEngine.currentState(info.getServiceId()).orElse(null),
                                       info.getCreated(),
                                       info.getUpdated());

    }

    private Optional<ExecutorSummary> toExecutorSummary(final ExecutorHostInfo hostInfo, boolean removed) {
        return hostInfo.getNodeData()
                .accept(new NodeDataVisitor<>() {
                    @Override
                    public Optional<ExecutorSummary> visit(ControllerNodeData controllerData) {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<ExecutorSummary> visit(ExecutorNodeData executorData) {
                        val backlistedState = clusterResourcesDB.isBlacklisted(hostInfo.getExecutorId())
                                              ? ExecutorState.BLACKLISTED
                                              : executorData.getExecutorState();
                        val executorState = removed ? ExecutorState.REMOVED
                                                    : backlistedState;
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
                                                               executorState));
                    }
                });
    }

    private boolean isInMaintenanceWindow() {
        return CommonUtils.isInMaintenanceWindow(clusterStateDB.currentState().orElse(null));
    }

    private Set<String> blacklistExecutorsInternal(Set<String> executorIds) {
        final var successfullyBlacklisted = executorIds.stream()
                .filter(executorId -> {
                    if (clusterResourcesDB.isBlacklisted(executorId)) {
                        log.warn("Executor {} is set to blacklisted already. We are going to skip this.", executorId);
                        return false;
                    }
                    val executor = clusterResourcesDB.currentSnapshot(executorId).orElse(null);
                    if (null != executor) {
                        val msgResponse = communicator.send(
                                new BlacklistExecutorMessage(MessageHeader.controllerRequest(),
                                                             new ExecutorAddress(executor.getExecutorId(),
                                                                                 executor.getNodeData().getHostname(),
                                                                                 executor.getNodeData().getPort(),
                                                                                 executor.getNodeData()
                                                                                         .getTransportType())));
                        if (msgResponse.getStatus().equals(MessageDeliveryStatus.ACCEPTED)) {
                            clusterResourcesDB.markBlacklisted(executorId);
                            log.info("Executors {} have been marked as blacklisted. Moving running instances",
                                     executorId);
                            eventBus.publish(new DroveExecutorBlacklistedEvent(executorMetadata(executor.getNodeData())));
                            return true;
                        }
                        else {
                            log.error("Error sending blacklist message to executor {}. Status: {}",
                                      executorId,
                                      msgResponse.getStatus());
                        }
                    }
                    return false;
                })
                .collect(Collectors.toUnmodifiableSet());
        if (successfullyBlacklisted.isEmpty()) {
            log.warn("No executor has been blacklisted. No apps to move.");
        }
        else {
            val status = blacklistingAppMovementManager.moveApps(successfullyBlacklisted);
            log.info("App movement manager acceptance status for executors {} is {}", successfullyBlacklisted, status);
        }
        return successfullyBlacklisted;
    }

    private Map<String, List<LocalServiceInstanceInfo>> instancesForService(String serviceId) {
        return localServiceStateDB.instances(
                        serviceId,
                        EnumSet.allOf(LocalServiceInstanceState.class),
                        true)
                .stream()
                .collect(Collectors.groupingBy(LocalServiceInstanceInfo::getExecutorId));
    }
}
