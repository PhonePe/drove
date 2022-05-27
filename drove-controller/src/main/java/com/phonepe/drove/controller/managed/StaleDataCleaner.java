package com.phonepe.drove.controller.managed;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.phonepe.drove.controller.engine.ApplicationEngine;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.InstanceInfoDB;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.ops.ApplicationDestroyOperation;
import io.appform.signals.signals.ScheduledSignal;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.Date;
import java.util.Set;
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
    private final InstanceInfoDB instanceInfoDB;
    private final LeadershipEnsurer leadershipEnsurer;
    private final ApplicationEngine applicationEngine;

    private final ScheduledSignal refresher;

    @Inject
    public StaleDataCleaner(
            ApplicationStateDB applicationStateDB,
            InstanceInfoDB instanceInfoDB,
            LeadershipEnsurer leadershipEnsurer,
            ApplicationEngine applicationEngine) {
        this(applicationStateDB, instanceInfoDB, leadershipEnsurer, applicationEngine, Duration.ofHours(6));
    }

    @VisibleForTesting
    StaleDataCleaner(
            ApplicationStateDB applicationStateDB,
            InstanceInfoDB instanceInfoDB,
            LeadershipEnsurer leadershipEnsurer,
            ApplicationEngine applicationEngine,
            Duration interval) {
        this.applicationStateDB = applicationStateDB;
        this.instanceInfoDB = instanceInfoDB;
        this.leadershipEnsurer = leadershipEnsurer;
        this.applicationEngine = applicationEngine;
        refresher = new ScheduledSignal(interval);
    }

    @Override
    public void start() {
        refresher.connect(HANDLER_NAME, this::cleanupData);
        log.info("Stale data cleaner started");
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
        val maxLastUpdated = new Date(new Date().getTime() - 7L * 24 * 60 * 60 * 1000); //7 days before now
        val allApps = applicationStateDB.applications(0, Integer.MAX_VALUE);
        val candidateAppIds = allApps
                .stream()
                .filter(applicationInfo -> applicationInfo.getUpdated().before(maxLastUpdated))
                .map(ApplicationInfo::getAppId)
                .filter(appId -> applicationEngine.applicationState(appId)
                        .map(applicationState -> applicationState.equals(ApplicationState.MONITORING))
                        .orElse(false))
                .toList();
        candidateAppIds.forEach(appId -> {
            val res = applicationEngine.handleOperation(new ApplicationDestroyOperation(appId, ClusterOpSpec.DEFAULT));
            log.info("App destroy command response for {} : {}", appId, res);
        });

        //Now cleanup stale instance info
        val otherApps = Sets.difference(
                allApps.stream()
                        .map(ApplicationInfo::getAppId)
                        .collect(Collectors.toUnmodifiableSet()),
                Set.copyOf(candidateAppIds));
        otherApps.stream()
                .flatMap(appId -> instanceInfoDB.oldInstances(appId, 0, Integer.MAX_VALUE)
                                         .stream()
                                         .filter(instanceInfo -> instanceInfo.getUpdated().before(maxLastUpdated)))
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
        log.info("Stale data check invocation at {} completed now", date);
    }
}
