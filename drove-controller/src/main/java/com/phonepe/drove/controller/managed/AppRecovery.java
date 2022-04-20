package com.phonepe.drove.controller.managed;

import com.phonepe.drove.controller.engine.ApplicationEngine;
import com.phonepe.drove.controller.engine.CommandValidator;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.ops.ApplicationCreateOperation;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 *
 */
@Order(25)
@Slf4j
@Singleton
public class AppRecovery implements Managed {

    private final ApplicationEngine applicationEngine;
    private final ApplicationStateDB applicationStateDB;

    @Inject
    public AppRecovery(
            LeadershipEnsurer leadershipEnsurer,
            ApplicationEngine applicationEngine,
            ApplicationStateDB applicationStateDB) {
        this.applicationEngine = applicationEngine;
        this.applicationStateDB = applicationStateDB;
        leadershipEnsurer.onLeadershipStateChanged().connect(this::handleLeadershipChange);
    }

    private void handleLeadershipChange(boolean isLeader) {
        if (isLeader) {
            log.info("This controller is now the leader.");
            applicationStateDB.applications(0, Integer.MAX_VALUE)
                    .forEach(applicationInfo -> {
                        log.info("Found app: {}. Starting it.", applicationInfo.getAppId());
                        val res = applicationEngine.handleOperation(new ApplicationCreateOperation(applicationInfo.getSpec(),
                                                                                                   applicationInfo.getInstances(),
                                                                                                   ClusterOpSpec.DEFAULT));
                        if (!res.getStatus().equals(CommandValidator.ValidationStatus.SUCCESS)) {
                            log.error("Error sending command to state machine. Error: " + res.getMessages());
                        }
                    });
        }
        else {
            log.info("This controller is not the leader anymore. All executors will be stopped");
            applicationEngine.stopAll();
        }
    }

    @Override
    public void start() throws Exception {

    }

    @Override
    public void stop() throws Exception {
        log.debug("Shut down {}", this.getClass().getSimpleName());
    }
}
