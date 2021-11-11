package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.ClockPulseGenerator;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.ops.ApplicationScaleOperation;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
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
public class ApplicationMonitor implements Managed {
    private static final Set<ApplicationState> SKIPPED_STATES = EnumSet.of(ApplicationState.INIT,
                                                                           ApplicationState.SUSPENDED,
                                                                           ApplicationState.FAILED);
    private final ClockPulseGenerator clockPulseGenerator
            = new ClockPulseGenerator("application-monitor", Duration.ofSeconds(5), Duration.ofSeconds(3));

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
        clockPulseGenerator.onPulse().connect(time -> {
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
                    if (SKIPPED_STATES.contains(state)) {
                        log.debug("Checks skipped on {} as it is in {} state", appId, state.name());
                        return;
                    }

                    val expectedInstances = app.getInstances();
                    val actualInstances = applicationStateDB.instanceCount(appId, InstanceState.HEALTHY);
                    if (actualInstances != expectedInstances) {
                        log.error("Number of instances for app {} is currently {}. Requested: {}, needs recovery.",
                                  appId, actualInstances, expectedInstances);
                        notifyOperation(new ApplicationScaleOperation(appId, expectedInstances, ClusterOpSpec.DEFAULT));
                    }
                });
    }
}
