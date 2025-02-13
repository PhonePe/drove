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
import com.google.common.collect.Sets;
import com.phonepe.drove.common.model.utils.Pair;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.engine.ApplicationLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.LocalServiceLifecycleManagementEngine;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.localserviceops.LocalServiceDestroyOperation;
import com.phonepe.drove.models.operation.ops.ApplicationDestroyOperation;
import io.appform.signals.signals.ScheduledSignal;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Cleans stale app and instances
 */
@Order(60)
@Singleton
@Slf4j
public class StaleDataCleaner implements Managed {
    private static final String HANDLER_NAME = "STALE_DATA_CLEANER";

    private final ApplicationStateDB applicationStateDB;
    private final ApplicationInstanceInfoDB instanceInfoDB;
    private final TaskDB taskDB;
    private final LocalServiceStateDB localServiceStateDB;
    private final LeadershipEnsurer leadershipEnsurer;
    private final ApplicationLifecycleManagementEngine applicationEngine;
    private final LocalServiceLifecycleManagementEngine localServiceEngine;
    private final ControllerOptions options;

    private final ScheduledSignal refresher;
    private final ClusterOpSpec defaultClusterOpSpec;

    @Inject
    public StaleDataCleaner(
            ApplicationStateDB applicationStateDB,
            ApplicationInstanceInfoDB instanceInfoDB,
            LeadershipEnsurer leadershipEnsurer,
            ApplicationLifecycleManagementEngine applicationEngine,
            TaskDB taskDB,
            ControllerOptions options,
            ClusterOpSpec defaultClusterOpSpec, LocalServiceStateDB localServiceStateDB,
            LocalServiceLifecycleManagementEngine localServiceEngine) {
        this(applicationStateDB,
             instanceInfoDB,
             taskDB, localServiceStateDB,
             leadershipEnsurer,
             applicationEngine, localServiceEngine,
             options,
             Duration.ofMillis(Objects.requireNonNullElse(options.getStaleCheckInterval(),
                                                          ControllerOptions.DEFAULT_STALE_CHECK_INTERVAL)
                                       .toMilliseconds()), defaultClusterOpSpec);
    }

    @VisibleForTesting
    @SuppressWarnings("java:S107")
    StaleDataCleaner(
            ApplicationStateDB applicationStateDB,
            ApplicationInstanceInfoDB instanceInfoDB,
            TaskDB taskDB, LocalServiceStateDB localServiceStateDB,
            LeadershipEnsurer leadershipEnsurer,
            ApplicationLifecycleManagementEngine applicationEngine,
            LocalServiceLifecycleManagementEngine localServiceEngine,
            ControllerOptions options,
            Duration interval,
            ClusterOpSpec defaultClusterOpSpec) {
        this.applicationStateDB = applicationStateDB;
        this.instanceInfoDB = instanceInfoDB;
        this.taskDB = taskDB;
        this.localServiceStateDB = localServiceStateDB;
        this.leadershipEnsurer = leadershipEnsurer;
        this.applicationEngine = applicationEngine;
        this.localServiceEngine = localServiceEngine;
        this.options = options;
        this.refresher = new ScheduledSignal(interval);
        this.defaultClusterOpSpec = defaultClusterOpSpec;
    }

    @Override
    public void start() {
        refresher.connect(HANDLER_NAME, this::cleanupData);
        log.info("Stale data cleaner started");
        cleanupData(new Date());
    }

    @Override
    public void stop() {
        refresher.disconnect(HANDLER_NAME);
        refresher.close();
        log.info("Stale data cleaner stopped");
    }

    private void cleanupData(Date date) {
        if (!leadershipEnsurer.isLeader()) {
            log.info("Skipping stale data cleanup as I'm not leader");
            return;
        }
        cleanupStaleApps(applicationStateDB.applications(0, Integer.MAX_VALUE));
        cleanupStaleTasks();
        cleanupStaleService(localServiceStateDB.services(0, Integer.MAX_VALUE));

        log.info("Stale data check invocation at {} completed now", date);
    }

    private void cleanupStaleTasks() {
        val maxLastTaskUpdated = new Date(System.currentTimeMillis() - Objects.requireNonNullElse(
                options.getStaleTaskAge(), ControllerOptions.DEFAULT_STALE_TASK_AGE).toMilliseconds());
        taskDB.cleanupTasks(task -> task.getState().isTerminal() && task.getUpdated().before(maxLastTaskUpdated));
    }

    private void cleanupStaleApps(List<ApplicationInfo> allApps) {
        val slateAppLifetime = new Date(
                System.currentTimeMillis() - Objects.requireNonNullElse(
                        options.getStaleAppAge(), ControllerOptions.DEFAULT_STALE_APP_AGE).toMilliseconds());
        val slateInstanceLifetime = new Date(
                System.currentTimeMillis() - Objects.requireNonNullElse(
                        options.getStaleInstanceAge(), ControllerOptions.DEFAULT_STALE_INSTANCE_AGE).toMilliseconds());
        val candidateAppIds = allApps
                .stream()
                .filter(applicationInfo -> applicationInfo.getUpdated().before(slateAppLifetime))
                .map(ApplicationInfo::getAppId)
                .filter(appId -> applicationEngine.currentState(appId)
                        .map(applicationState -> applicationState.equals(ApplicationState.MONITORING))
                        .orElse(false))
                .toList();
        candidateAppIds.forEach(appId -> {
            val res = applicationEngine.handleOperation(
                    new ApplicationDestroyOperation(appId, defaultClusterOpSpec));
            log.info("Stale app destroy command response for {} : {}", appId, res);
        });

        //Now cleanup stale instance info
        val otherApps = Sets.difference(
                allApps.stream()
                        .map(ApplicationInfo::getAppId)
                        .collect(Collectors.toUnmodifiableSet()),
                Set.copyOf(candidateAppIds));
        val maxInstances = options.getMaxStaleInstancesCount() > 0
                           ? options.getMaxStaleInstancesCount()
                           : ControllerOptions.DEFAULT_MAX_STALE_INSTANCES_COUNT;
        instanceInfoDB.oldInstances(otherApps)
                .forEach((appId, instances) -> {
                    var count = 0;
                    for (val instanceInfo : instances.stream()
                            .sorted(Comparator.comparing(InstanceInfo::getUpdated).reversed())
                            .toList()) {
                        count++;
                        if (count <= maxInstances && instanceInfo.getUpdated().after(slateInstanceLifetime)) {
                            continue;
                        }
                        val instanceId = instanceInfo.getInstanceId();
                        if (instanceInfoDB.deleteInstanceState(appId, instanceId)) {
                            log.info("Deleted stale instance info: {}/{}", appId, instanceId);
                        }
                        else {
                            log.warn("Could not delete stale instance info: {}/{}", appId, instanceId);
                        }
                    }
                });
    }

    private void cleanupStaleService(List<LocalServiceInfo> allServices) {
        val now = Instant.now();
        val staleServiceAge = Objects.requireNonNullElse(
                        options.getStaleServiceAge(), ControllerOptions.DEFAULT_STALE_SERVICE_AGE)
                .toMilliseconds();
        val staleInstanceAge = Objects.requireNonNullElse(
                        options.getStaleInstanceAge(), ControllerOptions.DEFAULT_STALE_INSTANCE_AGE)
                .toMilliseconds();

        val slateServiceLifetime = Date.from(now.minus(staleServiceAge, ChronoUnit.MILLIS));
        val slateInstanceLifetime = Date.from(now.minus(staleInstanceAge, ChronoUnit.MILLIS));

        val candidateServiceIds = allServices
                .stream()
                .filter(serviceInfo -> serviceInfo.getUpdated().before(slateServiceLifetime))
                .map(LocalServiceInfo::getServiceId)
                .filter(serviceId -> localServiceEngine.currentState(serviceId)
                        .map(serviceState -> serviceState.equals(LocalServiceState.INACTIVE))
                        .orElse(false))
                .toList();
        candidateServiceIds.forEach(serviceId -> {
            val res = localServiceEngine.handleOperation(new LocalServiceDestroyOperation(serviceId));
            log.info("Stale service destroy command response for {} : {}", serviceId, res);
        });

        //Now cleanup stale instance info
        val otherApps = Sets.difference(
                allServices.stream()
                        .map(LocalServiceInfo::getServiceId)
                        .collect(Collectors.toUnmodifiableSet()),
                Set.copyOf(candidateServiceIds));
        val maxInstances = options.getMaxStaleInstancesCount() > 0
                           ? options.getMaxStaleInstancesCount()
                           : ControllerOptions.DEFAULT_MAX_STALE_INSTANCES_COUNT;
        otherApps.stream()
                .map(serviceId -> Pair.of(serviceId, localServiceStateDB.oldInstances(serviceId)))
                .forEach(data -> {
                    val serviceId = data.getFirst();
                    var count = 0;
                    for (val instanceInfo : data.getSecond().stream()
                            .sorted(Comparator.comparing(LocalServiceInstanceInfo::getUpdated).reversed())
                            .toList()) {
                        count++;
                        if (count <= maxInstances && instanceInfo.getUpdated().after(slateInstanceLifetime)) {
                            continue;
                        }
                        val instanceId = instanceInfo.getInstanceId();
                        if (instanceInfoDB.deleteInstanceState(serviceId, instanceId)) {
                            log.info("Deleted stale service instance info: {}/{}", serviceId, instanceId);
                        }
                        else {
                            log.warn("Could not delete stale service instance info: {}/{}", serviceId, instanceId);
                        }
                    }
                });
    }
}
