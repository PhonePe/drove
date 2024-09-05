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
import dev.failsafe.RetryPolicy;

import java.time.Duration;
import java.util.Objects;

/**
 *
 */
@Slf4j
@NoArgsConstructor
public class ApplicationInstanceReadinessCheckAction extends ApplicationInstanceSingularCheckActionBase {

    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<InstanceState, ExecutorInstanceInfo> executeImpl(
            InstanceActionContext<ApplicationInstanceSpec> context, StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        val readinessCheckSpec = context.getInstanceSpec().getReadiness();
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
        try(val checker = ExecutorUtils.createChecker(context, currentState.getData(), readinessCheckSpec)) {
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
            return switch (result.getStatus()) {
                case HEALTHY -> StateData.from(currentState, InstanceState.READY);
                case STOPPED -> StateData.from(currentState, InstanceState.STOPPING);
                case UNHEALTHY -> StateData.errorFrom(currentState, InstanceState.READINESS_CHECK_FAILED, "Readiness check failed");
            };
        }
        catch (Exception e) {
            return StateData.errorFrom(currentState, InstanceState.READINESS_CHECK_FAILED, e.getMessage());
        }
    }

    @Override
    protected InstanceState defaultErrorState() {
        return InstanceState.READINESS_CHECK_FAILED;
    }

}
