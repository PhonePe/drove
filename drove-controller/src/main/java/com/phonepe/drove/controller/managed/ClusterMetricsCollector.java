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

package com.phonepe.drove.controller.managed;

import com.google.common.annotations.VisibleForTesting;
import com.phonepe.drove.controller.engine.ApplicationEngine;
import com.phonepe.drove.controller.metrics.ClusterMetricsRegistry;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskState;
import io.appform.functionmetrics.MonitoredFunction;
import io.appform.signals.signals.ScheduledSignal;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.lifecycle.ServerLifecycleListener;
import io.dropwizard.setup.Environment;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.eclipse.jetty.server.Server;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static com.phonepe.drove.controller.metrics.ClusterMetricNames.Gauges.*;

/**
 * Publishes cluster metrics
 */
@Singleton
@Slf4j
@Order(90)
public class ClusterMetricsCollector implements Managed, ServerLifecycleListener {

    private final ScheduledSignal refresher;

    private final ClusterResourcesDB clusterResourcesDB;
    private final ApplicationStateDB applicationStateDB;
    private final ApplicationEngine applicationEngine;
    private final LeadershipEnsurer leadershipEnsurer;
    private final ClusterMetricsRegistry metricsRegistry;

    @Inject
    public ClusterMetricsCollector(
            ClusterResourcesDB clusterResourcesDB,
            ApplicationStateDB applicationStateDB,
            ApplicationEngine applicationEngine,
            LeadershipEnsurer leadershipEnsurer,
            Environment environment,
            ClusterMetricsRegistry metricsRegistry) {
        this(clusterResourcesDB,
             applicationStateDB,
             applicationEngine,
             leadershipEnsurer,
             environment,
             metricsRegistry,
             new ScheduledSignal(Duration.ofSeconds(30)));
    }

    @VisibleForTesting
    ClusterMetricsCollector(
            ClusterResourcesDB clusterResourcesDB,
            ApplicationStateDB applicationStateDB,
            ApplicationEngine applicationEngine,
            LeadershipEnsurer leadershipEnsurer,
            Environment environment,
            ClusterMetricsRegistry metricsRegistry,
            ScheduledSignal refresher) {
        this.clusterResourcesDB = clusterResourcesDB;
        this.applicationStateDB = applicationStateDB;
        this.applicationEngine = applicationEngine;
        this.leadershipEnsurer = leadershipEnsurer;
        this.metricsRegistry = metricsRegistry;
        this.refresher = refresher;

        environment.lifecycle().addServerLifecycleListener(this);
    }


    @Override
    public void serverStarted(Server server) {
        refresher.connect("HANDLER", this::populateClusterMetrics);
        log.info("Cluster metrics collector started");
    }

    @Override
    public void stop() {
        refresher.disconnect("HANDLER");
        refresher.close();
        log.info("Cluster metrics collector shut down");
    }

    private void populateClusterMetrics(final Date now) {
        if (!leadershipEnsurer.isLeader()) {
            log.info("Not publishing cluster metrics as I am not the leader");
            return;
        }
        generateMetricsData();
        log.debug("Metrics updated for scan started at: {}", now);
    }

    @MonitoredFunction
    private void generateMetricsData() {

        val executors = clusterResourcesDB.currentSnapshot(false);

        var activeExecutorsCount = 0;
        var inactiveExecutorsCount = 0;
        var activeAppInstances = 0L;
        var inactiveAppInstances = 0L;
        var activeTaskInstances = 0L;
        var runningApps = 0;
        var totalApps = 0;

        val liveExecutors = new ArrayList<ExecutorHostInfo>();
        for (val executor : executors) {
            val nodeData = executor.getNodeData();
            if (nodeData.isBlacklisted()) {
                inactiveExecutorsCount++;
            }
            else {
                activeExecutorsCount++;
                liveExecutors.add(executor);
            }
            val appInstances = Objects.requireNonNullElse(nodeData.getInstances(),
                                                          List.<InstanceInfo>of());
            val healthyCount = appInstances.stream()
                    .filter(instance -> instance.getState().equals(InstanceState.HEALTHY))
                    .count();
            activeAppInstances += healthyCount;
            inactiveAppInstances += appInstances.size() - healthyCount;

            val taskInstances = Objects.requireNonNullElse(
                    nodeData.getTasks(),
                    List.<TaskInfo>of());
            val healthyTaskCount = taskInstances.stream()
                    .filter(instance -> instance.getState().equals(TaskState.RUNNING))
                    .count();
            activeTaskInstances += healthyTaskCount;
        }

        val clusterResources = ControllerUtils.summarizeResources(liveExecutors);

        val apps = applicationStateDB.applications(0, Integer.MAX_VALUE);
        for (val app : apps) {
            if (ApplicationState.RUNNING.equals(applicationEngine.applicationState(app.getAppId())
                                                        .orElse(ApplicationState.DESTROYED))) {
                runningApps++;
            }
            totalApps++;
        }
        metricsRegistry.setGaugeValue(CLUSTER_CPU_FREE, clusterResources.getFreeCores());
        metricsRegistry.setGaugeValue(CLUSTER_CPU_USED, clusterResources.getUsedCores());
        metricsRegistry.setGaugeValue(CLUSTER_CPU_TOTAL, clusterResources.getTotalCores());
        metricsRegistry.setGaugeValue(CLUSTER_MEMORY_FREE, clusterResources.getFreeMemory());
        metricsRegistry.setGaugeValue(CLUSTER_MEMORY_USED, clusterResources.getUsedMemory());
        metricsRegistry.setGaugeValue(CLUSTER_MEMORY_TOTAL, clusterResources.getTotalMemory());

        metricsRegistry.setGaugeValue(CLUSTER_EXECUTORS_ACTIVE, activeExecutorsCount);
        metricsRegistry.setGaugeValue(CLUSTER_EXECUTORS_INACTIVE, inactiveExecutorsCount);

        metricsRegistry.setGaugeValue(CLUSTER_APPLICATIONS_RUNNING, runningApps);
        metricsRegistry.setGaugeValue(CLUSTER_APPLICATIONS_TOTAL, totalApps);
        metricsRegistry.setGaugeValue(CLUSTER_APPLICATIONS_INSTANCES_ACTIVE, activeAppInstances);
        metricsRegistry.setGaugeValue(CLUSTER_APPLICATIONS_INSTANCES_INACTIVE, inactiveAppInstances);
        metricsRegistry.setGaugeValue(CLUSTER_TASK_INSTANCES_ACTIVE, activeTaskInstances);
    }

}
