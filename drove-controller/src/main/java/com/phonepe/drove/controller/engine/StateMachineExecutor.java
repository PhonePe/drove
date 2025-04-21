/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
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
import com.phonepe.drove.models.application.JobType;
import com.phonepe.drove.statemachine.Action;
import com.phonepe.drove.statemachine.ActionContext;
import com.phonepe.drove.statemachine.StateMachine;
import dev.failsafe.TimeoutExceededException;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.slf4j.MDC;

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
public abstract class StateMachineExecutor<T, D, S extends Enum<S>, C extends ActionContext<D>, A extends Action<T, S, C, D>> {

    public static final String MDC_PARAM = "appId";
    public static final String MDC_PARAM_DID = "deployableId";
    public static final String MDC_PARAM_DT = "deployableType";
    private final JobType jobType;
    @Getter
    private final String deployableId;
    @Getter
    private final StateMachine<T,D,S,C,A> stateMachine;
    private final ExecutorService executorService;
    private final ControllerRetrySpecFactory retrySpecFactory;
    private final ConsumingFireForgetSignal<StateMachineExecutor<T,D,S,C,A>> stateMachineCompleted;

    private Future<S> currentState;
    private final Lock checkLock = new ReentrantLock();
    private final Condition checkCondition = checkLock.newCondition();
    private final AtomicBoolean wake = new AtomicBoolean();

    protected StateMachineExecutor(
            JobType jobType,
            String deployableId,
            StateMachine<T,D,S,C,A> stateMachine,
            ExecutorService executorService,
            ControllerRetrySpecFactory retrySpecFactory,
            ConsumingFireForgetSignal<StateMachineExecutor<T,D,S,C,A>> stateMachineCompleted) {
        this.jobType = jobType;
        this.deployableId = deployableId;
        this.stateMachine = stateMachine;
        this.executorService = executorService;
        this.retrySpecFactory = retrySpecFactory;
        this.stateMachineCompleted = stateMachineCompleted;
    }

    @SuppressWarnings("java:S1181")
    public void start() {
        currentState = executorService.submit(() -> {
            log.info("Monitor started for app: {}", deployableId);
            MDC.put(MDC_PARAM, this.deployableId);
            MDC.put(MDC_PARAM_DID, this.deployableId);
            MDC.put(MDC_PARAM_DT, this.jobType.name());
            val pausedStates = pausedStates();
            S state = null;
            try {
                do {
                    try {
                        state = stateMachine.execute();
                    }
                    catch (Throwable t) {
                        log.error("Error running action: ", t);
                    }
                    if (pausedStates.contains(state)) {
                        log.info("State machine is being suspended for app: {}", deployableId);
                        checkLock.lock();
                        wake.set(false);
                        try {
                            while (!wake.get()) {
                                checkCondition.await();
                            }
                            log.info("State machine resumed for app: {}", deployableId);
                        }
                        finally {
                            checkLock.unlock();
                        }
                    }
                } while (null != state && !isTerminal(state));
                log.info("State machine exited with final state: {}", state);
                stateMachineCompleted.dispatch(this);
            }
            finally {
                MDC.remove(MDC_PARAM);
                MDC.remove(MDC_PARAM_DID);
                MDC.remove(MDC_PARAM_DT);
            }
            return state;
        });
    }

    public boolean notifyUpdate(final D update) {
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
                                                          deployableId, e.getException().getMessage()));
                if (status) {
                    log.info("State machine for app {} has shut down with final state {}",
                             deployableId, currentState.get());
                }
                else {
                    log.warn("Could not ensure state machine has shut down for {}. There might be a leak somewhere.",
                             deployableId);
                }
            }
            catch (InterruptedException e) {
                log.warn("State machine for {} has been interrupted", deployableId);
                Thread.currentThread().interrupt();
            }
            catch (TimeoutExceededException e) {
                log.error("Wait for SM for " + deployableId + " to stop has exceeded 60 secs. There might be thread leak.", e);
            }
            catch (Exception e) {
                if (e.getCause() instanceof InterruptedException) {
                    log.info("State machine for {} has been stopped by interruption", deployableId);
                    return;
                }
                log.error("State machine for " + deployableId + " shut down with exception: " + e.getMessage(), e);
            }
        }
    }

    protected abstract Set<S> pausedStates();
    protected abstract boolean isTerminal(S state);
}
