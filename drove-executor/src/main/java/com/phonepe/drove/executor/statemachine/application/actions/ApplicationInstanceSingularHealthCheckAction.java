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

package com.phonepe.drove.executor.statemachine.application.actions;

import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import com.phonepe.drove.models.application.CheckResult;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.statemachine.StateData;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.RetryPolicy;

import java.time.Duration;
import java.util.Objects;

/**
 *
 */
@Slf4j
@NoArgsConstructor
public class ApplicationInstanceSingularHealthCheckAction extends ApplicationInstanceSingularCheckActionBase {

    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<InstanceState, ExecutorInstanceInfo> executeImpl(
            InstanceActionContext<ApplicationInstanceSpec> context, StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        val healthcheck = context.getInstanceSpec().getHealthcheck();
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
                return StateData.from(currentState, InstanceState.STOPPING);
            }
        }
        val retryPolicy = new RetryPolicy<CheckResult>()
                .withDelay(Duration.ofMillis(healthcheck.getInterval().toMilliseconds()))
                .withMaxAttempts(healthcheck.getAttempts())
                .handle(Exception.class)
                .handleResultIf(result -> null == result || result.getStatus() != CheckResult.Status.HEALTHY);
        try(val checker = ExecutorUtils.createChecker(context, currentState.getData(), healthcheck)) {
            val result = checkWithRetry(
                    retryPolicy,
                    checker,
                    e -> {
                        val failure = e.getFailure();
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
                return StateData.from(currentState, InstanceState.HEALTHY);
            }
            else {
                log.warn("Instance still unhealthy with state: {}. Will be killing this.", status);
            }
        }
        catch (Exception e) {
            return StateData.errorFrom(currentState,
                                       InstanceState.STOPPING,
                                       "Error running health-checks: " + e.getMessage());
        }
        return StateData.from(currentState, InstanceState.STOPPING);
    }

    @Override
    protected InstanceState defaultErrorState() {
        return InstanceState.STOPPING;
    }

}
