package com.phonepe.drove.controller.statemachine.actions;

import com.phonepe.drove.common.ClockPulseGenerator;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statemachine.AppAction;
import com.phonepe.drove.controller.statemachine.AppActionContext;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ApplicationOperationVisitor;
import com.phonepe.drove.models.operation.ops.*;
import io.dropwizard.util.Duration;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
@Slf4j
public class AppOperationRouterAction extends AppAction {
    private final Lock checkLock = new ReentrantLock();
    private final Condition checkCondition = checkLock.newCondition();
    private final ClockPulseGenerator clockPulseGenerator = new ClockPulseGenerator("app-checker",
                                                                                    Duration.seconds(1),
                                                                                    Duration.seconds(5));
    private final AtomicBoolean check = new AtomicBoolean();
    private final ApplicationStateDB applicationStateDB;

    @Inject
    public AppOperationRouterAction(ApplicationStateDB applicationStateDB) {
        this.applicationStateDB = applicationStateDB;
    }

    @SneakyThrows
    @Override
    public StateData<ApplicationState, ApplicationInfo> execute(
            AppActionContext context, StateData<ApplicationState, ApplicationInfo> currentState) {
        clockPulseGenerator.onPulse().connect(time -> {
            checkLock.lock();
            try {
                check.set(true);
                checkCondition.signalAll();
            }
            finally {
                checkLock.unlock();
            }
        });
        while (true) {
            log.debug("Monitoring for commands");
            checkLock.lock();
            try {
                while (!check.get()) {
                    checkCondition.await();
                }
                val operation = null != context.getUpdate()
                                ? context.getUpdate().getOperation()
                                : null;
                if (null != operation) {
                    val newState = moveToNextState(currentState, operation).orElse(null);
                    if (null != newState) {
                        log.info("App move to new state: {}", newState);
                        return newState;
                    }
                }
                if (currentState.getState().equals(ApplicationState.MONITORING)) {
                    log.debug("App in created state. Nothing to do. Will wait for further commands.");
                    continue;
                }
                val checkResult = checkAppHealth(context, currentState).orElse(null);
                if (checkResult != null) {
                    return checkResult;
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            finally {
                check.set(false);
                checkLock.unlock();
                //Do not reset the operation in context here, it will be used by next action
            }
        }
    }

    @Override
    public void stop() {
        //TODO::IMPLEMENT THIS
    }

    protected Optional<StateData<ApplicationState, ApplicationInfo>> moveToNextState(
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation) {
        return operation.accept(new ApplicationOperationVisitor<>() {
            @Override
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationCreateOperation create) {
                return Optional.empty();
            }

            @Override
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationUpdateOperation update) {
                return Optional.empty(); //TODO
            }

            @Override
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationInfoOperation info) {
                return Optional.empty();
            }

            @Override
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationDestroyOperation destroy) {
                return Optional.of(StateData.from(currentState, ApplicationState.DESTROY_REQUESTED));
            }

            @Override
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationDeployOperation deploy) {
                return Optional.of(StateData.from(currentState, ApplicationState.DEPLOYMENT_REQUESTED));
            }

            @Override
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationScaleOperation scale) {
                return Optional.of(StateData.from(currentState, ApplicationState.SCALING_REQUESTED));
            }

            @Override
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationRestartOperation restart) {
                return Optional.of(StateData.from(currentState, ApplicationState.RESTART_REQUESTED));
            }

            @Override
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationSuspendOperation suspend) {
                return Optional.of(StateData.from(currentState, ApplicationState.SUSPEND_REQUESTED));
            }
        });
    }


    private Optional<StateData<ApplicationState, ApplicationInfo>> checkAppHealth(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState) {
        val appId = context.getAppId();
        val appInfo = applicationStateDB.application(appId).orElse(null);
        if(null == appInfo) {
            log.error("No app info found for app: {}", appId);
            return Optional.empty();
        }
        val healthyInstances = applicationStateDB.instances(appId, 0, Integer.MAX_VALUE)
                .stream()
                .filter(instance -> instance.getState().equals(InstanceState.HEALTHY))
                .count();
        val requestedInstances = appInfo.getInstances();
        if (healthyInstances != requestedInstances) {
            log.error("Number of instances for app {} is currently {}. Requested: {}, needs recovery.",
                      appId, healthyInstances, requestedInstances);
            return Optional.of(StateData.errorFrom(currentState,
                                                   ApplicationState.PARTIAL_OUTAGE,
                                                   "Current instances " + healthyInstances + " Requested: " + requestedInstances));
        }
        return Optional.empty();
    }

}
