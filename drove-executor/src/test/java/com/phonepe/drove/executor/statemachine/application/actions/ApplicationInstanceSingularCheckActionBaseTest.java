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

import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.checker.Checker;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.application.CheckResult;
import com.phonepe.drove.models.application.checks.CheckMode;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.statemachine.StateData;
import dev.failsafe.RetryPolicy;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
class ApplicationInstanceSingularCheckActionBaseTest {
    @Test
    void testSuccess() {
        val action = new ApplicationInstanceSingularCheckActionBase() {
            @Override
            protected StateData<InstanceState, ExecutorInstanceInfo> executeImpl(
                    InstanceActionContext<ApplicationInstanceSpec> context,
                    StateData<InstanceState, ExecutorInstanceInfo> currentState) {
                val result = checkWithRetry(
                        RetryPolicy.<CheckResult>builder()
                                .withMaxAttempts(1)
                                .build(),
                        new Checker() {
                            @Override
                            public CheckMode mode() {
                                return CheckMode.HTTP;
                            }

                            @Override
                            public void close() throws Exception {

                            }

                            @Override
                            public CheckResult call() throws Exception {
                                return CheckResult.healthy();
                            }
                        },
                        e -> {});
                return result.getStatus() == CheckResult.Status.HEALTHY
                       ? StateData.from(currentState, InstanceState.HEALTHY)
                       : StateData.from(currentState, InstanceState.UNHEALTHY);

            }

            @Override
            protected InstanceState defaultErrorState() {
                return null;
            }
        };
        assertEquals(InstanceState.HEALTHY,
                     action.executeImpl(null, StateData.create(InstanceState.STARTING, null)).getState());
    }

    @Test
    void testSuccessFailure() {
        val failed = new AtomicBoolean(false);
        val action = new ApplicationInstanceSingularCheckActionBase() {
            @Override
            protected StateData<InstanceState, ExecutorInstanceInfo> executeImpl(
                    InstanceActionContext<ApplicationInstanceSpec> context,
                    StateData<InstanceState, ExecutorInstanceInfo> currentState) {
                try {
                    val result = checkWithRetry(
                            RetryPolicy.<CheckResult>builder()
                                    .withMaxAttempts(1)
                                    .build(),
                            new Checker() {
                                @Override
                                public CheckMode mode() {
                                    return CheckMode.HTTP;
                                }

                                @Override
                                public void close() {

                                }

                                @Override
                                public CheckResult call() {
                                    throw new RuntimeException();
                                }
                            },
                            e -> failed.set(Objects.nonNull(e.getException())));
                    return result.getStatus() == CheckResult.Status.HEALTHY
                           ? StateData.from(currentState, InstanceState.HEALTHY)
                           : StateData.from(currentState, InstanceState.UNHEALTHY);

                }
                catch (Exception e) {
                    return StateData.from(currentState, InstanceState.LOST);
                }
            }

            @Override
            protected InstanceState defaultErrorState() {
                return null;
            }
        };

        assertEquals(
                InstanceState.LOST,
                action.executeImpl(null, StateData.create(InstanceState.STARTING, null)).
                        getState());
        CommonTestUtils.waitUntil(failed::get);

        assertTrue(failed.get());
    }

    @Test
    void testSuccessStop() {
        val action = new ApplicationInstanceSingularCheckActionBase() {
            @Override
            protected StateData<InstanceState, ExecutorInstanceInfo> executeImpl(
                    InstanceActionContext<ApplicationInstanceSpec> context,
                    StateData<InstanceState, ExecutorInstanceInfo> currentState) {
                try {
                    val result = checkWithRetry(
                            RetryPolicy.<CheckResult>builder()
                                    .withMaxAttempts(1)
                                    .build(),
                            new Checker() {
                                @Override
                                public CheckMode mode() {
                                    return CheckMode.HTTP;
                                }

                                @Override
                                public void close() {

                                }

                                @Override
                                public CheckResult call() {
                                    throw new RuntimeException();
                                }
                            },
                            e -> {});
                    return result.getStatus() == CheckResult.Status.HEALTHY
                           ? StateData.from(currentState, InstanceState.HEALTHY)
                           : StateData.from(currentState, InstanceState.STOPPED);

                }
                catch (Exception e) {
                    return StateData.from(currentState, InstanceState.LOST);
                }
            }

            @Override
            protected InstanceState defaultErrorState() {
                return null;
            }
        };
        action.stop();
        assertEquals(
                InstanceState.STOPPED,
                action.executeImpl(null, StateData.create(InstanceState.STARTING, null)).
                        getState());

    }
}