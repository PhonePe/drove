package com.phonepe.drove.executor.statemachine.application.actions;

import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.application.ApplicationInstanceAction;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import com.phonepe.drove.models.application.CheckResult;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.statemachine.StateData;
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
public class ApplicationInstanceReadinessCheckAction extends ApplicationInstanceAction {
    private final AtomicBoolean stop = new AtomicBoolean();

    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<InstanceState, ExecutorInstanceInfo> executeImpl(
            InstanceActionContext<ApplicationInstanceSpec> context, StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        val readinessCheckSpec = context.getInstanceSpec().getReadiness();
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
        try(val checker = ExecutorUtils.createChecker(context, currentState.getData(), readinessCheckSpec)) {
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

    @Override
    public void stop() {
        stop.set(true);
    }

}
