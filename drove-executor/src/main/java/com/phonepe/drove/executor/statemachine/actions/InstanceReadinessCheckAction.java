package com.phonepe.drove.executor.statemachine.actions;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.checker.Checker;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import com.phonepe.drove.models.application.CheckResult;
import com.phonepe.drove.models.instance.InstanceState;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
@Slf4j
@NoArgsConstructor
public class InstanceReadinessCheckAction extends InstanceAction {
    private final AtomicBoolean stop = new AtomicBoolean();

    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<InstanceState, ExecutorInstanceInfo> executeImpl(
            InstanceActionContext context, StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        val readinessCheckSpec = context.getInstanceSpec().getReadiness();
        final Checker checker = ExecutorUtils.createChecker(context, currentState.getData(), readinessCheckSpec);
        val initDelay = Objects.requireNonNullElse(readinessCheckSpec.getInitialDelay(),
                                                   io.dropwizard.util.Duration.seconds(0)).toMilliseconds();
        if(initDelay > 0) {
            try {
                Thread.sleep(readinessCheckSpec.getInitialDelay().toMilliseconds());
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        val retryPolicy = new RetryPolicy<CheckResult>()
                .withDelay(Duration.ofMillis(readinessCheckSpec.getInterval().toMilliseconds()))
                .withMaxAttempts(readinessCheckSpec.getAttempts())
                .handle(Exception.class)
                .handleResultIf(result -> null == result || result.getStatus() != CheckResult.Status.HEALTHY);
        try {
            val result = Failsafe.with(List.of(retryPolicy))
                    .onComplete(e -> {
                        val failure = e.getFailure();
                        if (failure != null) {
                            log.error("Readiness checks completed with error: {}", failure.getMessage());
                        }
                        else {
                            val checkResult = e.getResult();
                            log.info("Readiness check result: {}", checkResult);
                        }
                    })
                    .get(() -> {
                        if(stop.get()) {
                            return CheckResult.stopped();
                        }
                        return checker.call();
                    });
            return switch (result.getStatus()) {
                case HEALTHY -> StateData.create(InstanceState.READY, currentState.getData());
                case STOPPED -> StateData.create(InstanceState.STOPPING, currentState.getData());
                case UNHEALTHY -> StateData.create(InstanceState.READINESS_CHECK_FAILED, currentState.getData());
            };
        }
        catch (Exception e) {
            return StateData.errorFrom(currentState, InstanceState.READINESS_CHECK_FAILED, e.getMessage());
        }
    }

    @Override
    public void stop() {
        stop.set(true);
    }

}
