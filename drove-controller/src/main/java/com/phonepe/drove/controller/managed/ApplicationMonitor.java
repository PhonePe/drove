package com.phonepe.drove.controller.managed;

import com.phonepe.drove.controller.engine.ApplicationEngine;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.instance.InstanceState;
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
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
@Slf4j
@Order(30)
@Singleton
public class ApplicationMonitor implements Managed {
    private static final Set<ApplicationState> SKIPPED_STATES = EnumSet.of(ApplicationState.INIT,
                                                                           ApplicationState.DESTROYED,
                                                                           ApplicationState.FAILED,
                                                                           ApplicationState.SCALING_REQUESTED);
    private final ScheduledSignal refreshSignal = ScheduledSignal.builder()
            .initialDelay(Duration.ofSeconds(5))
            .interval(Duration.ofSeconds(3))
            .build();

    private final AtomicBoolean check = new AtomicBoolean();
    private final Lock checkLock = new ReentrantLock();
    private final Condition checkCond = checkLock.newCondition();

    private final ApplicationStateDB applicationStateDB;
    private final ApplicationEngine engine;

    @Inject
    public ApplicationMonitor(
            ApplicationStateDB applicationStateDB,
            ApplicationEngine engine) {
        this.applicationStateDB = applicationStateDB;
        this.engine = engine;
    }

    @Override
    public void start() throws Exception {
        refreshSignal.connect(time -> {
/*             checkLock.lock();
             try {
                 check.set(true);
                 checkCond.signalAll();
             }
             finally {
                 checkLock.unlock();
             }*/
            checkAllApps();
        });
    }

    @Override
    public void stop() throws Exception {

    }

    public void notifyOperation(final ApplicationOperation operation) {
        engine.handleOperation(operation);
    }

    private void checkAllApps() {
        applicationStateDB.applications(0, Integer.MAX_VALUE)
                .forEach(app -> {
                    val appId = app.getAppId();
                    val state = engine.applicationState(appId).orElse(ApplicationState.FAILED);
                    if (state != ApplicationState.RUNNING) {
                        log.trace("Checks skipped on {} as it is in {} state", appId, state.name());
                        return;
                    }

                    val expectedInstances = app.getInstances();
                    val actualInstances = applicationStateDB.instanceCount(appId, InstanceState.HEALTHY);
                    if (actualInstances != expectedInstances) {
                        log.error("Number of instances for app {} is currently {}. Requested: {}, needs recovery.",
                                  appId, actualInstances, expectedInstances);
                        notifyOperation(new ApplicationRecoverOperation(appId));
                    }
                });
    }
}
