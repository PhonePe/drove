/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.drove.controller.ui.support;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.phonepe.drove.common.coverageutils.IgnoreInJacocoGeneratedReport;
import com.phonepe.drove.common.discovery.leadership.LeadershipObserver;
import com.phonepe.drove.common.model.utils.Pair;
import com.phonepe.drove.controller.engine.ApplicationLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.LocalServiceLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.TaskEngine;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB.ClusterResourcesSummary;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.ClusterStateDB;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.api.ClusterSummary;
import com.phonepe.drove.models.api.DashboardData;
import com.phonepe.drove.models.api.DashboardData.ExecutorStats;
import com.phonepe.drove.models.api.LocalServiceSummary;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.interfaces.DeploymentSpec;
import com.phonepe.drove.models.localservice.ActivationState;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskState;

import io.appform.functionmetrics.MonitoredFunction;
import io.appform.signals.signals.ScheduledSignal;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class DashboardDataSource {

    private static final String DASHBOARD_REFRESHER_SIGNAL_HANDLER = "dashboard-refresher";
    private static final Duration DASHBOARD_REFRESH_INTERVAL = Duration.ofSeconds(30);

    private final ApplicationLifecycleManagementEngine appEngine; 
    private final ApplicationStateDB applicationStateDB;
    private final ApplicationInstanceInfoDB instanceInfoDB;
    private final TaskEngine taskEngine;
    private final LocalServiceStateDB localServiceStateDB;
    private final LocalServiceLifecycleManagementEngine localServiceEngine;
    private final ClusterResourcesDB clusterResourcesDB;

    private final Lock dashboardDataLock = new ReentrantLock();

    private final LeadershipEnsurer leadershipEnsurer;

    private final LeadershipObserver leadershipObserver;
    private final ClusterStateDB clusterStateDB;

    private final ScheduledSignal dashboardDataRefreshSignal;

    private final AtomicReference<DashboardData> cachedDashboardData = new AtomicReference<>(null);

    @Inject
    @IgnoreInJacocoGeneratedReport
    @SuppressWarnings("java:S107")
    public DashboardDataSource(ApplicationStateDB applicationStateDB,
                               TaskEngine taskEngine,
                               LocalServiceStateDB localServiceStateDB,
                               ApplicationLifecycleManagementEngine appEngine,
                               ClusterResourcesDB clusterResourcesDB,
                               ApplicationInstanceInfoDB instanceInfoDB,
                               LocalServiceLifecycleManagementEngine localServiceEngine,
                               LeadershipEnsurer leadershipEnsurer,
                               LeadershipObserver leadershipObserver,
                               ClusterStateDB clusterStateDB) {
        this(applicationStateDB,
                taskEngine,
                localServiceStateDB,
                appEngine,
                clusterResourcesDB,
                instanceInfoDB,
                localServiceEngine,
                leadershipEnsurer,
                leadershipObserver,
                clusterStateDB,
                DASHBOARD_REFRESH_INTERVAL);
    }

    @VisibleForTesting
    @SuppressWarnings("java:S107")
    DashboardDataSource(ApplicationStateDB applicationStateDB,
                        TaskEngine taskEngine,
                        LocalServiceStateDB localServiceStateDB,
                        ApplicationLifecycleManagementEngine appEngine,
                        ClusterResourcesDB clusterResourcesDB,
                        ApplicationInstanceInfoDB instanceInfoDB,
                        LocalServiceLifecycleManagementEngine localServiceEngine,
                        LeadershipEnsurer leadershipEnsurer,
                        LeadershipObserver leadershipObserver,
                        ClusterStateDB clusterStateDB,
                        Duration refreshInterval) {
        this.appEngine = appEngine;
        this.applicationStateDB = applicationStateDB;
        this.instanceInfoDB = instanceInfoDB;
        this.taskEngine = taskEngine;
        this.localServiceStateDB = localServiceStateDB;
        this.localServiceEngine = localServiceEngine;
        this.clusterResourcesDB = clusterResourcesDB;
        this.leadershipEnsurer = leadershipEnsurer;
        this.leadershipObserver = leadershipObserver;
        this.clusterStateDB = clusterStateDB;
        this.dashboardDataRefreshSignal = ScheduledSignal.builder()
        .interval(refreshInterval)
        .build();

        dashboardDataRefreshSignal.connect(DASHBOARD_REFRESHER_SIGNAL_HANDLER, this::refreshDashboardData);
    }

    public Optional<DashboardData> current() {
        val locked = dashboardDataLock.tryLock();
        if(locked) {
            try {
                return Optional.ofNullable(cachedDashboardData.get());
            }
            finally {
                dashboardDataLock.unlock();
            }
        }
        log.debug("Could not acquire lock to read dashboard data. Returning empty");
        return Optional.empty();
    }

    private void refreshDashboardData(Date triggeredAt) {
        if(!leadershipEnsurer.isLeader()) {
            log.debug("Not refreshing dashboard data as this instance is not the leader");
            return;
        }
        if(!dashboardDataLock.tryLock()) {
            log.debug("Not refreshing dashboard data as another refresh is in progress");
            return;
        }
        try {
            refreshDashboardDataInternal();
            log.info("Refreshed dashboard data in {} ms",
                    System.currentTimeMillis() - triggeredAt.toInstant().toEpochMilli());
        }
        finally {
            dashboardDataLock.unlock();
        }
    }

    @MonitoredFunction
    private void refreshDashboardDataInternal() {
        val clusterSummary = ControllerUtils.summarizeResources(clusterResourcesDB.currentSnapshot(true));
        val applications = applicationStateDB.applications(0, Integer.MAX_VALUE);
        val tasks = taskEngine.tasks(EnumSet.allOf(TaskState.class));
        val services = localServiceStateDB.services(0, Integer.MAX_VALUE);
        val appStats = computeAppStats(clusterSummary, applications);
        val executors = clusterResourcesDB.currentSnapshot(true);
        cachedDashboardData.set(DashboardData.builder()
                .clusterSummary(computeClusterSummary(applications,
                                                      tasks,
                                                      services,
                                                      clusterSummary))
                .appStats(appStats.getFirst())
                .taskStats(computeTaskStats(clusterSummary, tasks))
                .serviceStats(computeServiceStats(clusterSummary, services))
                .executorStats(computeExecutorStats(clusterSummary, executors))
                .generatedAt(new Date())
                .build());
    }

    private ExecutorStats computeExecutorStats(ClusterResourcesSummary clusterSummary,
                                                        List<ExecutorHostInfo> executors) {
        val executorCountByState = new HashMap<ExecutorState, Long>(
                EnumSet.allOf(ExecutorState.class)
                .stream()
                .collect(Collectors.toMap(Function.identity(), state -> 0L)));
        val utilizations = new ArrayList<Double>();
        var maxUtilization = 0.0;
        var minUtilization = 0.0d;
        for(val executor : executors) {
            executorCountByState.compute(executor.getNodeData().getExecutorState(),
                                        (state, count) -> Objects.requireNonNullElse(count, 0L) + 1);
            val cpus = executor.getNodeData().getState().getCpus();
            val usedCores = countCores(cpus.getUsedCores());
            val freeCores = countCores(cpus.getFreeCores());
            val cpuUtilization = (double)usedCores / (usedCores + freeCores);

            val memory = executor.getNodeData().getState().getMemory();
            val usedMemory = countMemory(memory.getUsedMemory());
            val freeMemory = countMemory(memory.getFreeMemory());
            val memoryUtilization = (double)usedMemory / (usedMemory + freeMemory);

            // We use dominant utilization to capture the fact that an executor
            // is only as utilized as its most utilized resource.
            val dominantUtilization = Math.max(cpuUtilization, memoryUtilization);
            utilizations.add(dominantUtilization);
            maxUtilization = Math.max(maxUtilization, dominantUtilization);
            minUtilization = (minUtilization == 0.0) ? dominantUtilization : Math.min(minUtilization, dominantUtilization);
        }
        val averageUtilization = utilizations.stream()
            .mapToDouble(Double::doubleValue).average().orElse(0.0);
        // We define balance score as the coefficient of variation (stddev/mean) of executor utilizations.
        // A lower score means more balanced resource usage across executors.
        val balaceScore = Math.sqrt(utilizations.stream()
            .mapToDouble(util -> Math.pow(util - averageUtilization, 2))
            .average()
            .orElse(0.0)) / averageUtilization;
        return ExecutorStats.builder()
                .executorCountByState(executorCountByState)
                .utilization(DashboardData.UtilizationStats.builder()
                        .averageUtilization(averageUtilization)
                        .highestUtilization(maxUtilization)
                        .lowestUtilization(minUtilization)
                        .balanceScore(balaceScore)
                        .build())
                .build();
    }

    private int countCores(Map<Integer, Set<Integer>> cores) {
        return cores.values().stream().mapToInt(Set::size).sum();
    }

    private long countMemory(Map<Integer, Long> memory) {
        return memory.values().stream().mapToLong(Long::longValue).sum();
    }

    private Pair<DashboardData.AppStats, Set<String>> computeAppStats(
            final ClusterResourcesSummary clusterSummary,
            final List<ApplicationInfo> applications) {
        val scorables = new ArrayList<Pair<ApplicationInfo, Long>>();
        val appCountByState = new HashMap<ApplicationState, Long>(
                EnumSet.allOf(ApplicationState.class)
                .stream()
                .collect(Collectors.toMap(Function.identity(), state -> 0L)));
        val appIds = new ArrayList<String>();
        val appNames = new HashSet<String>();
        for(val app : applications) {
            val appState = appEngine.currentState(app.getAppId()).orElse(null);
            if(null == appState) {
                log.warn("Could not find state for app {}. Skipping it in dashboard stats", app.getAppId());
                continue;
            }

            appCountByState.compute(appState,
                    (state, count) -> Objects.requireNonNullElse(count, 0L) + 1);
            scorables.add(Pair.of(app, score(app, clusterSummary)));
            appIds.add(app.getAppId());
            appNames.add(app.getSpec().getName());
        }
        val healthyInstances = instanceInfoDB.healthyInstances(appIds);
        val totalHealthyInstances = healthyInstances.values()
            .stream()
            .mapToInt(List::size)
            .sum();
        val topApps = scorables.stream()
            .sorted((lhs, rhs) -> Long.compare(rhs.getSecond(), lhs.getSecond())) //descending 
            .limit(10)
            .map(data -> ControllerUtils.toAppSummary(
                        data.getFirst(),
                        appEngine,
                        healthyInstances
                        .getOrDefault(data.getFirst().getAppId(), List.of()).size()
                        ))
            .toList();
        return Pair.of(DashboardData.AppStats.builder()
                .appCountByState(appCountByState)
                .topApps(List.copyOf(topApps))
                .totalHealthyInstances(totalHealthyInstances)
                .build(),
                appNames);
    }

    private DashboardData.ServiceStats computeServiceStats(
            final ClusterResourcesSummary clusterSummary,
            final List<LocalServiceInfo> services) {
        val serviceCountByState = new HashMap<LocalServiceState, Long>(
                EnumSet.allOf(LocalServiceState.class)
                .stream()
                .collect(Collectors.toMap(Function.identity(), state -> 0L)));
        val serviceCountByActivationState = new HashMap<ActivationState, Long>();
        val scorables = new ArrayList<Pair<LocalServiceInfo, Long>>(5);
        for (val service : services) {
            val serviceState = localServiceEngine.currentState(service.getServiceId()).orElse(null);
            if (null == serviceState) {
                log.warn("Could not find state for service {}. Skipping it in dashboard stats", service.getServiceId());
                continue;
            }

            serviceCountByState.compute(serviceState,
                                        (state, count) -> Objects.requireNonNullElse(count, 0L) + 1);
            serviceCountByActivationState.compute(service.getActivationState(),
                                                  (state, count) -> Objects.requireNonNullElse(count, 0L) + 1);
            scorables.add(Pair.of(service,
                                  score(service.getSpec(), 1, clusterSummary))); //Instance count is irrelevant here
        }
        val instancesForServices = new HashMap<String, List<LocalServiceInstanceInfo>>();
        services.stream()
                .map(LocalServiceInfo::getServiceId)
                .forEach(serviceId -> instancesForServices.put(serviceId, localServiceStateDB.instances(serviceId,
                                                                                     EnumSet.allOf(LocalServiceInstanceState.class),
                                                                                     false)));
        val totalHealthyInstances = instancesForServices.values()
                .stream()
                .flatMap(List::stream)
                .filter(instanceInfo -> instanceInfo.getState().equals(LocalServiceInstanceState.HEALTHY))
                .count();
        val topServices = scorables.stream()
                .sorted((lhs, rhs) -> Long.compare(rhs.getSecond(), lhs.getSecond())) //descending 
                .limit(5)
                .map(data -> summarizeService(data.getFirst(), instancesForServices.getOrDefault(data.getFirst().getServiceId(), List.of())))
                .toList();
        return DashboardData.ServiceStats.builder()
                .serviceCountByState(serviceCountByState)
                .serviceCountByActivationState(Map.copyOf(serviceCountByActivationState))
                .topServices(List.copyOf(topServices))
                .totalHealthyInstances(totalHealthyInstances)
                .build();

    }

    private LocalServiceSummary summarizeService(final LocalServiceInfo serviceInfo,
            final List<LocalServiceInstanceInfo> instances) {
        val instancesPerExecutor = instances
                .stream()
                .collect(Collectors.groupingBy(LocalServiceInstanceInfo::getExecutorId));
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
        return ControllerUtils.toLocalServiceSummary(serviceInfo,
                                                     knownInstances,
                                                     healthyInstances,
                                                     localServiceEngine);
    }

    private DashboardData.TaskStats computeTaskStats(
            final ClusterResourcesSummary clusterSummary,
            final List<TaskInfo> tasks) {
        val taskCountByState = new HashMap<TaskState, Long>(
                EnumSet.allOf(TaskState.class)
                .stream()
                .collect(Collectors.toMap(Function.identity(), state -> 0L)));
        val scorables = new ArrayList<Pair<TaskInfo, Long>>(5);
        tasks.stream()
            .forEach(task -> {
                    taskCountByState.compute(task.getState(),
                                             (state, count) -> Objects.requireNonNullElse(count, 0L) + 1);
                    val elapsdTime = System.currentTimeMillis() - task.getCreated().toInstant().toEpochMilli();
                    scorables.add(Pair.of(task, elapsdTime));
                });
        return DashboardData.TaskStats.builder()
                .taskCountByState(taskCountByState)
                .topTasks(scorables.stream()
                                  .sorted((lhs, rhs) -> Long.compare(rhs.getSecond(), lhs.getSecond())) //descending 
                                  .limit(5)
                                  .map(Pair::getFirst)
                                  .toList())
                .build();
    }

    private ClusterSummary computeClusterSummary(
            List<ApplicationInfo> applications,
            List<TaskInfo> tasks,
            List<LocalServiceInfo> services,
            ClusterResourcesSummary clusterResourcesSummary) {
        return ControllerUtils.computeClusterSummary(
                    leadershipObserver,
                    clusterStateDB,
                    applications,
                    tasks,
                    services,
                    appEngine,
                    localServiceEngine,
                    clusterResourcesSummary);
    } 

    private static long score(final ApplicationInfo applicationInfo, ClusterResourcesSummary clusterSummary) {
        return  score(applicationInfo.getSpec(), applicationInfo.getInstances(), clusterSummary);
    }

    //We use dominant resource fairness scoring
    private static long score(final DeploymentSpec spec, long instances, ClusterResourcesSummary clusterSummary) {
        val totalCPU = ControllerUtils.totalCPU(spec, instances) / Math.max(1, clusterSummary.getTotalCores());
        val totalMemory = ControllerUtils.totalMemory(spec, instances) / Math.max(1, clusterSummary.getTotalMemory());
        return Math.max(totalCPU, totalMemory);
    }
        
}

