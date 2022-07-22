package com.phonepe.drove.executor.statemachine.application.actions;

import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.checker.Checker;
import com.phonepe.drove.executor.model.ExecutorApplicationInstanceInfo;
import com.phonepe.drove.executor.statemachine.application.ApplicationInstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import com.phonepe.drove.models.application.CheckResult;
import com.phonepe.drove.models.instance.InstanceState;
import io.appform.functionmetrics.MonitoredFunction;
import com.phonepe.drove.statemachine.StateData;
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
public class ApplicationInstanceSingularHealthCheckAction extends ApplicationInstanceAction {
    private final AtomicBoolean stop = new AtomicBoolean();

    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<InstanceState, ExecutorApplicationInstanceInfo> executeImpl(
            InstanceActionContext<ApplicationInstanceSpec> context, StateData<InstanceState, ExecutorApplicationInstanceInfo> currentState) {
        val healthcheck = context.getInstanceSpec().getHealthcheck();
        final Checker checker = ExecutorUtils.createChecker(currentState.getData(), healthcheck);
        val initDelay = Objects.requireNonNullElse(healthcheck.getInitialDelay(),
                                                   io.dropwizard.util.Duration.seconds(0)).toMilliseconds();
        if (initDelay > 0) {
            try {
                Thread.sleep(healthcheck.getInitialDelay().toMilliseconds());
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
            val status = result.getStatus();
            switch (status) {
                case HEALTHY:
                    return StateData.from(currentState, InstanceState.HEALTHY);
                case STOPPED:
                case UNHEALTHY:
                    log.warn("Instance still unhealthy with state: {}. Will be killing this.", status);
                default:
                    return StateData.from(currentState, InstanceState.STOPPING);
            }
        }
        catch (Exception e) {
            return StateData.errorFrom(currentState,
                                       InstanceState.STOPPING,
                                       "Error running health-checks: " + e.getMessage());
        }
    }

    @Override
    protected InstanceState defaultErrorState() {
        return InstanceState.STOPPING;
    }

    @Override
    public void stop() {
        stop.set(true);
    }

}
