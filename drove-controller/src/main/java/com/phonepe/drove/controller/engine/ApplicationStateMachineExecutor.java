package com.phonepe.drove.controller.engine;

import com.phonepe.drove.controller.statemachine.ApplicationStateMachine;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
@Slf4j
public class ApplicationStateMachineExecutor {
    private static final Set<ApplicationState> PAUSED_STATES = EnumSet.of(ApplicationState.MONITORING, ApplicationState.RUNNING);

    public static final String MDC_PARAM = "appId";
    private final String appId;
    @Getter
    private final ApplicationStateMachine stateMachine;
    private final ExecutorService executorService;
    private Future<ApplicationState> currentState;
    private final Lock checkLock = new ReentrantLock();
    private final Condition checkCondition = checkLock.newCondition();
    private final AtomicBoolean wake = new AtomicBoolean();

    public ApplicationStateMachineExecutor(
            String appId,
            ApplicationStateMachine stateMachine, ExecutorService executorService) {
        this.appId = appId;
        this.stateMachine = stateMachine;
        this.executorService = executorService;
    }

    public void start() {
        currentState = executorService.submit(() -> {
            MDC.put(MDC_PARAM, this.appId);
            ApplicationState state = null;
            try {
                do {
                    try {
                        state = stateMachine.execute();
                    } catch (Throwable t) {
                        log.error("Error running action: ", t);
                    }
                    if(PAUSED_STATES.contains(state)) {
                        log.info("State machine is being suspended");
                        checkLock.lock();
                        wake.set(false);
                        try {
                            while (!wake.get()) {
                                checkCondition.await();
                            }
                            log.info("State machine resumed");
                        }
                        finally {
                            checkLock.unlock();
                        }
                    }
                } while (null != state && !state.isTerminal());
            }
            finally {
                MDC.remove(MDC_PARAM);
            }
            return state;
        });
    }

    public boolean notifyUpdate(final ApplicationOperation update) {
        if(stateMachine.notifyUpdate(update)) {
            checkLock.lock();
            try {
                wake.set(true);
                checkCondition.signalAll();
            }
            finally {
                checkLock.unlock();
            }
            return true;
        }
        return false;
    }

    public void stop() {
        if (null != currentState) {
            currentState.cancel(true);
            try {
                log.info("Application {} exited with state {}", appId, currentState.get());
            }
            catch (ExecutionException e) {
                log.error("Error getting value from app future for " + appId, e);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
