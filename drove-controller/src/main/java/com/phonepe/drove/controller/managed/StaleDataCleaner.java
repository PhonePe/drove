package com.phonepe.drove.controller.managed;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.engine.ApplicationEngine;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.ops.ApplicationDestroyOperation;
import com.phonepe.drove.models.taskinstance.TaskState;
import io.appform.signals.signals.ScheduledSignal;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
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
    private final LeadershipEnsurer leadershipEnsurer;
    private final ApplicationEngine applicationEngine;

    private final ControllerOptions options;

    private final ScheduledSignal refresher;

    @Inject
    public StaleDataCleaner(
            ApplicationStateDB applicationStateDB,
            ApplicationInstanceInfoDB instanceInfoDB,
            LeadershipEnsurer leadershipEnsurer,
            ApplicationEngine applicationEngine,
            TaskDB taskDB, ControllerOptions options) {
        this(applicationStateDB,
             instanceInfoDB,
             taskDB,
             leadershipEnsurer,
             applicationEngine,
             options,
             Duration.ofMillis(Objects.requireNonNullElse(options.getStaleCheckInterval(),
                                                          ControllerOptions.DEFAULT_STALE_CHECK_INTERVAL).toMilliseconds()));
    }

    @VisibleForTesting
    StaleDataCleaner(
            ApplicationStateDB applicationStateDB,
            ApplicationInstanceInfoDB instanceInfoDB,
            TaskDB taskDB,
            LeadershipEnsurer leadershipEnsurer,
            ApplicationEngine applicationEngine,
            ControllerOptions options,
            Duration interval) {
        this.applicationStateDB = applicationStateDB;
        this.instanceInfoDB = instanceInfoDB;
        this.taskDB = taskDB;
        this.leadershipEnsurer = leadershipEnsurer;
        this.applicationEngine = applicationEngine;
        this.options = options;
        this.refresher = new ScheduledSignal(interval);
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
        val allApps = applicationStateDB.applications(0, Integer.MAX_VALUE);
        cleanupStaleApps(allApps);
        cleanupStaleTasks(allApps);

        log.info("Stale data check invocation at {} completed now", date);
    }

    private void cleanupStaleTasks(List<ApplicationInfo> allApps) {
        val appNames = allApps.stream().map(ai -> ai.getSpec().getName()).distinct().toList();
        val taskTerminalState = EnumSet.allOf(TaskState.class)
                .stream()
                .filter(TaskState::isTerminal)
                .collect(Collectors.toSet());
        val maxLastTaskUpdated = new Date(System.currentTimeMillis() - Objects.requireNonNullElse(
                options.getStaleTaskAge(), ControllerOptions.DEFAULT_STALE_TASK_AGE).toMilliseconds());

        taskDB.tasks(appNames, taskTerminalState, true)
                .values()
                .stream()
                .flatMap(Collection::stream)
                .filter(ti -> ti.getUpdated().before(maxLastTaskUpdated))
                .forEach(ti -> {
                    val sourceAppName = ti.getSourceAppName();
                    val taskId = ti.getTaskId();
                    if(taskDB.deleteTask(sourceAppName, taskId)) {
                        log.info("Deleted stale task {}/{}", sourceAppName, taskId);
                    }
                });
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
                .filter(appId -> applicationEngine.applicationState(appId)
                        .map(applicationState -> applicationState.equals(ApplicationState.MONITORING))
                        .orElse(false))
                .toList();
        candidateAppIds.forEach(appId -> {
            val res = applicationEngine.handleOperation(new ApplicationDestroyOperation(appId, ClusterOpSpec.DEFAULT));
            log.info("Stale app destroy command response for {} : {}", appId, res);
        });

        //Now cleanup stale instance info
        val otherApps = Sets.difference(
                allApps.stream()
                        .map(ApplicationInfo::getAppId)
                        .collect(Collectors.toUnmodifiableSet()),
                Set.copyOf(candidateAppIds));
        instanceInfoDB.oldInstances(otherApps)
                .entrySet()
                .stream()
                .flatMap(entry -> entry.getValue()
                        .stream()
                        .filter(instanceInfo -> instanceInfo.getUpdated().before(slateInstanceLifetime)))
                .forEach(instanceInfo -> {
                    val appId = instanceInfo.getAppId();
                    val instanceId = instanceInfo.getInstanceId();
                    if (instanceInfoDB.deleteInstanceState(appId, instanceId)) {
                        log.info("Deleted stale instance info: {}/{}", appId, instanceId);
                    }
                    else {
                        log.warn("Could not delete stale instance info: {}/{}", appId, instanceId);
                    }
                });
    }
}
