package com.phonepe.drove.controller.managed;

import com.phonepe.drove.controller.engine.ApplicationEngine;
import com.phonepe.drove.controller.engine.TaskEngine;
import com.phonepe.drove.controller.engine.ValidationStatus;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.ops.ApplicationCreateOperation;
import com.phonepe.drove.models.taskinstance.TaskState;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 */
@Order(25)
@Slf4j
@Singleton
public class AppRecovery implements Managed {

    private final ApplicationEngine applicationEngine;
    private final TaskEngine taskEngine;
    private final ApplicationStateDB applicationStateDB;
    private final TaskDB taskDB;
    private final ClusterOpSpec defaultClusterOpSpec;

    @Inject
    public AppRecovery(
            LeadershipEnsurer leadershipEnsurer,
            ApplicationEngine applicationEngine,
            TaskEngine taskEngine,
            ApplicationStateDB applicationStateDB,
            TaskDB taskDB,
            ClusterOpSpec defaultClusterOpSpec) {
        this.applicationEngine = applicationEngine;
        this.taskEngine = taskEngine;
        this.applicationStateDB = applicationStateDB;
        this.taskDB = taskDB;
        this.defaultClusterOpSpec = defaultClusterOpSpec;
        leadershipEnsurer.onLeadershipStateChanged().connect(this::handleLeadershipChange);
    }

    @Override
    public void start() {
        log.info("Application recover manager started");
    }

    @Override
    public void stop() {
        log.debug("Shut down {}", this.getClass().getSimpleName());
    }

    private void handleLeadershipChange(boolean isLeader) {
        if (isLeader) {
            log.info("This controller is now the leader.");
            val allApps = applicationStateDB.applications(0, Integer.MAX_VALUE);
            recoverApps(allApps);
            recoverTasks(allApps);
        }
        else {
            log.info("This controller is not the leader anymore. All executors will be stopped");
            applicationEngine.stopAll();
        }
    }

    private void recoverApps(List<ApplicationInfo> allApps) {
        allApps
                .forEach(applicationInfo -> {
                    val appId = applicationInfo.getAppId();
                    log.info("Found app: {}. Starting it.", appId);
                    try {
                        val res = applicationEngine.handleOperation(
                                new ApplicationCreateOperation(applicationInfo.getSpec(),
                                                               applicationInfo.getInstances(),
                                                               defaultClusterOpSpec));
                        if (!res.getStatus().equals(ValidationStatus.SUCCESS)) {
                            log.error("Error sending command to state machine. Error: " + res.getMessages());
                        }
                    }
                    catch (Exception e) {
                        log.error("Error recovering state machine for " + appId, e);
                    }
                });
    }

    private void recoverTasks(List<ApplicationInfo> allApps) {
        val appNames = allApps.stream().map(app -> app.getSpec().getName()).collect(Collectors.toSet());
        log.debug("Task recovery to be attempted for the following app names: {}", appNames);
        val recoveryStates = EnumSet.allOf(TaskState.class)
                .stream()
                .filter(state -> !state.isTerminal())
                .collect(Collectors.toSet());
        appNames.forEach(
                        appName -> { //This is done one by one to avoid memory blowup for busy clusters
                            log.info("Attempting to recover tasks for: {}", appName);
                            val pendingTasks = taskDB.tasks(List.of(appName), recoveryStates, true);
                            pendingTasks.getOrDefault(appName, List.of())
                                    .forEach(task -> {
                                        val runner = taskEngine.registerTaskRunner(appName, task.getTaskId());
                                        if (null != runner) {
                                            log.info("Task {}/{} recovered", appName, task.getTaskId());
                                        }
                                        else {
                                            log.info("Task {}/{} could not be recovered", appName, task.getTaskId());
                                        }
                                    });
                        });
    }
}
