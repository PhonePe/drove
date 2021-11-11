package com.phonepe.drove.controller.statemachine.actions;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.controller.statemachine.AppAction;
import com.phonepe.drove.controller.statemachine.AppActionContext;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ApplicationOperationVisitor;
import com.phonepe.drove.models.operation.ops.*;
import io.appform.signals.signals.ScheduledSignal;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.Duration;
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
    private final ScheduledSignal clockPulseGenerator = new ScheduledSignal(Duration.ofSeconds(5));
    private final AtomicBoolean check = new AtomicBoolean();

    @SneakyThrows
    @Override
    public StateData<ApplicationState, ApplicationInfo> execute(
            AppActionContext context, StateData<ApplicationState, ApplicationInfo> currentState) {
        clockPulseGenerator.connect(time -> {
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
            log.trace("Monitoring for commands");
            checkLock.lock();
            try {
                while (!check.get()) {
                    checkCondition.await();
                }
                val operation = context.getUpdate()
                        .orElse(null);
                if (null != operation) {
                    log.info("Received command of type: {}", operation.getType());
                    val newState = moveToNextState(currentState, operation).orElse(null);
                    if (null != newState) {
                        log.info("App move to new state: {}", newState);
                        return newState;
                    }
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
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationStopInstancesOperation stopInstances) {
                return Optional.of(StateData.from(currentState, ApplicationState.STOP_INSTANCES_REQUESTED));
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

            @Override
            public Optional<StateData<ApplicationState, ApplicationInfo>> visit(ApplicationRecoverOperation recover) {
                return Optional.of(StateData.from(currentState, ApplicationState.OUTAGE_DETECTED));
            }
        });
    }

}
