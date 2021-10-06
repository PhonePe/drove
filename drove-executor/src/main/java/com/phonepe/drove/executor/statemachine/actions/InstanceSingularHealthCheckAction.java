package com.phonepe.drove.executor.statemachine.actions;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.Utils;
import com.phonepe.drove.executor.checker.Checker;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.application.CheckResult;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
@Slf4j
@NoArgsConstructor
public class InstanceSingularHealthCheckAction extends InstanceAction {
    private final AtomicBoolean stop = new AtomicBoolean();

    @Override
    protected StateData<InstanceState, ExecutorInstanceInfo> executeImpl(
            InstanceActionContext context, StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        val healthcheck = context.getInstanceSpec().getHealthcheck();
        final Checker checker = Utils.createChecker(context, currentState.getData(), healthcheck);
        val initDelay = Objects.requireNonNullElse(healthcheck.getInitialDelay(),
                                                   io.dropwizard.util.Duration.seconds(0)).toMilliseconds();
        if (initDelay > 0) {
            try {
                Thread.sleep(healthcheck.getInitialDelay().toMilliseconds());
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        val retryPolicy = new RetryPolicy<CheckResult>()
                .withDelay(Duration.ofMillis(healthcheck.getInterval().toMilliseconds()))
                .withMaxAttempts(3)
                .handle(Exception.class)
                .handleResultIf(result -> null == result || result.getStatus() != CheckResult.Status.HEALTHY);
        try {
            val result = Failsafe.with(retryPolicy)
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
                        if (stop.get()) {
                            return CheckResult.stopped();
                        }
                        return checker.call();
                    });
            switch (result.getStatus()) {
                case HEALTHY:
                    return StateData.create(InstanceState.HEALTHY, currentState.getData());
                case STOPPED:
                case UNHEALTHY:
                    log.info("Instance still unhealthy. Will be killing this.");
                default:
                    return StateData.create(InstanceState.STOPPING, currentState.getData());
            }
        }
        catch (Exception e) {
            return StateData.errorFrom(currentState,
                                       InstanceState.STOPPING,
                                       "Error running health-checks: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        stop.set(true);
    }

}
