package com.phonepe.drove.controller.managed;

import com.google.common.annotations.VisibleForTesting;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.controller.engine.ApplicationEngine;
import com.phonepe.drove.controller.engine.ValidationStatus;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.ClusterStateDB;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ops.ApplicationRecoverOperation;
import io.appform.signals.signals.ScheduledSignal;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.Date;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.phonepe.drove.models.instance.InstanceState.HEALTHY;

/**
 *
 */
@Slf4j
@Order(30)
@Singleton
public class ApplicationMonitor implements Managed {

    private static final String HANDLER_NAME = "APP_CHECK_MONITOR";
    private final ScheduledSignal refreshSignal = ScheduledSignal.builder()
            .initialDelay(Duration.ofSeconds(5))
            .interval(Duration.ofSeconds(30))
            .build();

    private final ApplicationStateDB applicationStateDB;
    private final ApplicationInstanceInfoDB instanceInfoDB;
    private ClusterStateDB clusterStateDB;
    private final ApplicationEngine engine;
    private final LeadershipEnsurer leadershipEnsurer;

    @Inject
    public ApplicationMonitor(
            ApplicationStateDB applicationStateDB,
            ApplicationInstanceInfoDB instanceInfoDB,
            ClusterStateDB clusterStateDB,
            ApplicationEngine engine,
            LeadershipEnsurer leadershipEnsurer) {
        this.applicationStateDB = applicationStateDB;
        this.instanceInfoDB = instanceInfoDB;
        this.clusterStateDB = clusterStateDB;
        this.engine = engine;
        this.leadershipEnsurer = leadershipEnsurer;
    }

    @Override
    public void start() throws Exception {
        refreshSignal.connect(HANDLER_NAME, this::checkAllApps);
    }

    @Override
    public void stop() throws Exception {
        log.debug("Shutting down {}", this.getClass().getSimpleName());
        refreshSignal.disconnect(HANDLER_NAME);
        refreshSignal.close();
        log.debug("Shut down {}", this.getClass().getSimpleName());
    }

    @VisibleForTesting
    public void checkAllApps(final Date checkTime) {
        if(!leadershipEnsurer.isLeader()) {
            log.info("Skipping app check as I'm not the leader");
            return;
        }
        if(CommonUtils.isInMaintenanceWindow(clusterStateDB.currentState().orElse(null))) {
            log.warn("Application check skipped as cluster is in maintenance window");
            return;
        }
        val apps = applicationStateDB.applications(0, Integer.MAX_VALUE)
                .stream()
                .collect(Collectors.toMap(ApplicationInfo::getAppId, Function.identity()));
        val instances = instanceInfoDB.instanceCount(apps.keySet(), HEALTHY);
        apps.values()
                .forEach(app -> {
                    val appId = app.getAppId();
                    val state = engine.applicationState(appId).orElse(ApplicationState.FAILED);
                    if (state != ApplicationState.RUNNING && state != ApplicationState.MONITORING) {
                        log.trace("Checks skipped on {} as it is in {} state", appId, state.name());
                        return;
                    }

                    val expectedInstances = app.getInstances();
                    instanceInfoDB.markStaleInstances(appId);
                    val actualInstances = instances.getOrDefault(appId, 0L);
                    if (actualInstances != expectedInstances) {
                        log.error("Number of instances for app {} is currently {}. Requested: {}, needs recovery.",
                                  appId, actualInstances, expectedInstances);
                        notifyOperation(new ApplicationRecoverOperation(appId));
                    }
                });
        log.debug("Application check triggered at {} is completed.", checkTime);
    }

    private void notifyOperation(final ApplicationOperation operation) {
        val res = engine.handleOperation(operation);
        if(!res.getStatus().equals(ValidationStatus.SUCCESS)) {
            log.error("Error sending command to state machine. Error: " + res.getMessages());
        }
    }

}
