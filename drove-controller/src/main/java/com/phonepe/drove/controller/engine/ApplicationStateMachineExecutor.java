/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.controller.statemachine.applications.ApplicationStateMachine;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.TimeoutExceededException;
import org.slf4j.MDC;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.phonepe.drove.common.CommonUtils.waitForAction;

/**
 *
 */
@Slf4j
public class ApplicationStateMachineExecutor {
    private static final Set<ApplicationState> PAUSED_STATES = EnumSet.of(ApplicationState.MONITORING,
                                                                          ApplicationState.RUNNING);

    public static final String MDC_PARAM = "appId";
    @Getter
    private final String appId;
    @Getter
    private final ApplicationStateMachine stateMachine;
    private final ExecutorService executorService;
    private final ControllerRetrySpecFactory retrySpecFactory;
    private final ConsumingFireForgetSignal<ApplicationStateMachineExecutor> stateMachineCompleted;

    private Future<ApplicationState> currentState;
    private final Lock checkLock = new ReentrantLock();
    private final Condition checkCondition = checkLock.newCondition();
    private final AtomicBoolean wake = new AtomicBoolean();

    public ApplicationStateMachineExecutor(
            String appId,
            ApplicationStateMachine stateMachine,
            ExecutorService executorService,
            ControllerRetrySpecFactory retrySpecFactory,
            ConsumingFireForgetSignal<ApplicationStateMachineExecutor> stateMachineCompleted) {
        this.appId = appId;
        this.stateMachine = stateMachine;
        this.executorService = executorService;
        this.retrySpecFactory = retrySpecFactory;
        this.stateMachineCompleted = stateMachineCompleted;
    }

    public void start() {
        currentState = executorService.submit(() -> {
            log.info("Monitor started for app: {}", appId);
            MDC.put(MDC_PARAM, this.appId);
            ApplicationState state = null;
            try {
                do {
                    try {
                        state = stateMachine.execute();
                    }
                    catch (Throwable t) {
                        log.error("Error running action: ", t);
                    }
                    if (PAUSED_STATES.contains(state)) {
                        log.info("State machine is being suspended for app: {}", appId);
                        checkLock.lock();
                        wake.set(false);
                        try {
                            while (!wake.get()) {
                                checkCondition.await();
                            }
                            log.info("State machine resumed for app: {}", appId);
                        }
                        finally {
                            checkLock.unlock();
                        }
                    }
                } while (null != state && !state.isTerminal());
                log.info("State machine exited with final state: {}", state);
                stateMachineCompleted.dispatch(this);
            }
            finally {
                MDC.remove(MDC_PARAM);
            }
            return state;
        });
    }

    public boolean notifyUpdate(final ApplicationOperation update) {
        if (stateMachine.notifyUpdate(update)) {
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
            stateMachine.stop();
            val retryPolicy = CommonUtils.<Boolean>policy(retrySpecFactory.appStateMachineRetrySpec(), r -> !r);
            try {
                val status = waitForAction(retryPolicy,
                                           () -> currentState.isDone(),
                                           e -> log.trace("Completion wait for {} completed with error: {}",
                                                          appId, e.getFailure().getMessage()));
                if (status) {
                    log.info("State machine for app {} has shut down with final state {}", appId, currentState.get());
                }
                else {
                    log.warn("Could not ensure state machine has shut down for {}. There might be a leak somewhere.",
                             appId);
                }
            }
            catch (InterruptedException e) {
                log.warn("State machine for {} has been interrupted", appId);
                Thread.currentThread().interrupt();
            }
            catch (TimeoutExceededException e) {
                log.error("Wait for SM for " + appId + " to stop has exceeded 60 secs. There might be thread leak.", e);
            }
            catch (Exception e) {
                if (e.getCause() instanceof InterruptedException) {
                    log.info("State machine for {} has been stopped by interruption", appId);
                    return;
                }
                log.error("State machine for " + appId + " shut down with exception: " + e.getMessage(), e);
            }
        }
    }
}
