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
import lombok.val;
import net.jodah.failsafe.RetryPolicy;
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
                val result = checkWithRetry(new RetryPolicy<CheckResult>().withMaxAttempts(1),
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
                    val result = checkWithRetry(new RetryPolicy<CheckResult>().withMaxAttempts(1),
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
                                                e -> failed.set(Objects.nonNull(e.getFailure())));
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
                    val result = checkWithRetry(new RetryPolicy<CheckResult>().withMaxAttempts(1),
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