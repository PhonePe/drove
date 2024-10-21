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
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.Duration;
import java.util.Objects;

/**
 *
 */
@Slf4j
public abstract class CommonInstanceSingularHealthCheckAction<E extends DeployedExecutionObjectInfo, S extends Enum<S>,
        T extends DeploymentUnitSpec> extends CommonInstanceSingularCheckActionBase<E, S, T> {
    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<S, E> executeImpl(InstanceActionContext<T> context, StateData<S, E> currentState) {
        val healthcheck = healthcheck(context.getInstanceSpec());
        var initialDelay = Objects.requireNonNullElse(healthcheck.getInitialDelay(),
                                                      io.dropwizard.util.Duration.seconds(0)).toMilliseconds();
        if(context.isRecovered()) {
            log.info("This state machine is in recovery flow. Health check initial delay will be ignored");
            initialDelay = 0;
        }
        if (initialDelay > 0) {
            log.info("Waiting {} ms before running initial health check", initialDelay);
            try {
                Thread.sleep(initialDelay);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return StateData.from(currentState, defaultErrorState());
            }
        }
        val retryPolicy = RetryPolicy.<CheckResult>builder()
                .withDelay(Duration.ofMillis(healthcheck.getInterval().toMilliseconds()))
                .withMaxAttempts(healthcheck.getAttempts())
                .handle(Exception.class)
                .handleResultIf(result -> null == result || result.getStatus() != CheckResult.Status.HEALTHY)
                .build();
        try(val checker = ExecutorUtils.createChecker(context, currentState.getData(), healthcheck)) {
            val result = checkWithRetry(
                    retryPolicy,
                    checker,
                    e -> {
                        val failure = e.getException();
                        if (failure != null) {
                            log.error("Initial health checks completed with error: {}", failure.getMessage());
                        }
                        else {
                            val checkResult = e.getResult();
                            log.info("Initial health check result: {}", checkResult);
                        }
                    });
            val status = result.getStatus();
            if(status == CheckResult.Status.HEALTHY) {
                return healthyState(currentState);
            }
            else {
                log.warn("Instance still unhealthy with state: {}. Will be killing this.", status);
            }
        }
        catch (Exception e) {
            return StateData.errorFrom(currentState,
                                       defaultErrorState(),
                                       "Error running health-checks: " + e.getMessage());
        }
        return StateData.from(currentState, defaultErrorState());
    }

    protected abstract CheckSpec healthcheck(T instanceSpec);

    protected abstract StateData<S, E> healthyState(StateData<S,E> currentState);

}
