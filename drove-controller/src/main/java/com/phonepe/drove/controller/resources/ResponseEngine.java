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

import static com.phonepe.drove.controller.utils.ControllerUtils.totalCPU;
import static com.phonepe.drove.controller.utils.ControllerUtils.totalMemory;
import static com.phonepe.drove.models.api.ApiResponse.failure;
import static com.phonepe.drove.models.api.ApiResponse.success;
import static com.phonepe.drove.models.instance.InstanceState.HEALTHY;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.coverageutils.IgnoreInJacocoGeneratedReport;
import com.phonepe.drove.common.discovery.leadership.LeadershipObserver;
import com.phonepe.drove.controller.engine.ApplicationLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.LocalServiceLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.TaskEngine;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.event.EventStore;
import com.phonepe.drove.controller.managed.BlacklistingManager;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB.ClusterResourcesSummary;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.ClusterStateDB;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.controller.ui.support.DashboardDataSource;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.controller.utils.EventUtils;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.api.AppDetails;
import com.phonepe.drove.models.api.AppSummary;
import com.phonepe.drove.models.api.BlacklistOperationResponse;
import com.phonepe.drove.models.api.ClusterSummary;
import com.phonepe.drove.models.api.DashboardData;
import com.phonepe.drove.models.api.DroveEventsList;
import com.phonepe.drove.models.api.DroveEventsSummary;
import com.phonepe.drove.models.api.ExecutorSummary;
import com.phonepe.drove.models.api.ExposedAppInfo;
import com.phonepe.drove.models.api.LocalServiceSummary;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.common.ClusterState;
import com.phonepe.drove.models.common.ClusterStateData;
import com.phonepe.drove.models.events.DroveEvent;
import com.phonepe.drove.models.events.events.DroveClusterMaintenanceModeRemovedEvent;
import com.phonepe.drove.models.events.events.DroveClusterMaintenanceModeSetEvent;
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

import lombok.val;
import lombok.extern.slf4j.Slf4j;

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
    private final BlacklistingManager blacklistingManager;
    private final DashboardDataSource dashboardDataSource;

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
            BlacklistingManager blacklistingAppMovementManager,
            DashboardDataSource dashboardDataSource) {
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
        this.blacklistingManager = blacklistingAppMovementManager;
        this.dashboardDataSource = dashboardDataSource;
    }

    public ApiResponse<DashboardData> dashboardData() {
        return dashboardDataSource.current()
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.failure("Could not fetch dashboard data"));
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
        return success(
                ControllerUtils.computeClusterSummary(
                    leadershipObserver,
                    clusterStateDB,
                    applicationStateDB.applications(0, Integer.MAX_VALUE),
                    taskEngine.tasks(EnumSet.allOf(TaskState.class)),
                    localServiceStateDB.services(0, Integer.MAX_VALUE),
                    applicationEngine,
                    localServiceEngine,
                    ControllerUtils.summarizeResources(
                        clusterResourcesDB.currentSnapshot(true))));
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

    public ApiResponse<BlacklistOperationResponse> blacklistExecutors(final Set<String> executorIds) {
        if (CommonUtils.isInMaintenanceWindow(clusterStateDB.currentState().orElse(null))) {
            return ApiResponse.failure("Cluster is in maintenance mode");
        }
        return ApiResponse.success(blacklistingManager.blacklistExecutors(executorIds));
    }

    public ApiResponse<BlacklistOperationResponse> unblacklistExecutors(final Set<String> executorIds) {
        if (CommonUtils.isInMaintenanceWindow(clusterStateDB.currentState().orElse(null))) {
            return ApiResponse.failure("Cluster is in maintenance mode");
        }
        return ApiResponse.success(blacklistingManager.unblacklistExecutors(executorIds));
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
        return ControllerUtils.toAppSummary(info,
                applicationEngine,
                instanceCounts.getOrDefault(info.getAppId(), 0L));
    }

    private LocalServiceSummary toLocalServiceSummary(
            final LocalServiceInfo info,
            Map<String, List<LocalServiceInstanceInfo>> instancesPerExecutor) {
        val knownInstances = instancesPerExecutor.values()
                         .stream()
                         .flatMap(List::stream)
                         .filter(instanceInfo -> LocalServiceInstanceState.ACTIVE_STATES
                                 .contains(instanceInfo.getState()))
                         .count();
        val healthyInstances = instancesPerExecutor.values()
                 .stream()
                 .flatMap(List::stream)
                 .filter(instanceInfo -> instanceInfo.getState()
                         .equals(LocalServiceInstanceState.HEALTHY))
                 .count();
        return ControllerUtils.toLocalServiceSummary(info, knownInstances, healthyInstances, localServiceEngine);
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
                        val backlistedState = executorData.getExecutorState();
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

    private Map<String, List<LocalServiceInstanceInfo>> instancesForService(String serviceId) {
        return localServiceStateDB.instances(
                        serviceId,
                        EnumSet.allOf(LocalServiceInstanceState.class),
                        true)
                .stream()
                .collect(Collectors.groupingBy(LocalServiceInstanceInfo::getExecutorId));
    }
}
