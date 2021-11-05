package com.phonepe.drove.controller.engine;

import com.phonepe.drove.controller.statemachine.ApplicationStateMachine;
import com.phonepe.drove.controller.statemachine.ApplicationUpdateData;
import com.phonepe.drove.models.application.ApplicationState;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 *
 */
@Slf4j
public class ApplicationMonitor {
    public static final String MDC_PARAM = "appId";
    private final String appId;
    private final ApplicationStateMachine stateMachine;
    private final ExecutorService executorService;
    private Future<ApplicationState> currentState;

    public ApplicationMonitor(
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
                } while (!state.isTerminal());
            }
            finally {
                MDC.remove(MDC_PARAM);
            }
            return state;
        });
        
    }

    public void notifyUpdate(final ApplicationUpdateData update) {
        stateMachine.notifyUpdate(update);
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
