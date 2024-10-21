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

package com.phonepe.drove.executor.statemachine.common.actions;

import com.phonepe.drove.common.model.DeploymentUnitSpec;
import com.phonepe.drove.executor.model.DeployedExecutionObjectInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import com.phonepe.drove.models.application.CheckResult;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.statemachine.StateData;
import dev.failsafe.RetryPolicy;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.Duration;
import java.util.Objects;

/**
 *
 */
@Slf4j
public abstract class CommonInstanceReadinessCheckAction<E extends DeployedExecutionObjectInfo, S extends Enum<S>,
        T extends DeploymentUnitSpec> extends CommonInstanceSingularCheckActionBase<E, S, T> {
    @Override
    protected StateData<S, E> executeImpl(InstanceActionContext<T> context, StateData<S, E> currentState) {
        val readinessCheckSpec = readinessCheckSpec(context.getInstanceSpec());
        var initDelay = Objects.requireNonNullElse(readinessCheckSpec.getInitialDelay(),
                                                   io.dropwizard.util.Duration.seconds(0)).toMilliseconds();
        if (context.isRecovered()) {
            log.info("This state machine is in recovery context. Readiness check initial delay will be ignored.");
        }
        else {
            if (initDelay > 0) {
                log.info("Waiting {} ms before running readiness checks", initDelay);
                try {
                    Thread.sleep(readinessCheckSpec.getInitialDelay().toMilliseconds());
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        val retryPolicy = RetryPolicy.<CheckResult>builder()
                .withDelay(Duration.ofMillis(readinessCheckSpec.getInterval().toMilliseconds()))
                .withMaxAttempts(readinessCheckSpec.getAttempts())
                .handle(Exception.class)
                .handleResultIf(result -> null == result || result.getStatus() != CheckResult.Status.HEALTHY)
                .build();
        try (val checker = ExecutorUtils.createChecker(context, currentState.getData(), readinessCheckSpec)) {
            val result = checkWithRetry(
                    retryPolicy,
                    checker,
                    e -> {
                        val failure = e.getException();
                        if (failure != null) {
                            log.error("Readiness checks completed with error: {}", failure.getMessage());
                        }
                        else {
                            val checkResult = e.getResult();
                            log.info("Readiness check result: {}", checkResult);
                        }
                    });
            return handleResult(currentState, result);
        }
        catch (Exception e) {
            return errorState(currentState, e);
        }
    }

    protected abstract CheckSpec readinessCheckSpec(T spec);

    protected abstract StateData<S,E> handleResult(StateData<S,E> currentState, CheckResult result);

    protected abstract StateData<S, E> errorState(StateData<S, E> currentState, Throwable e);

}
