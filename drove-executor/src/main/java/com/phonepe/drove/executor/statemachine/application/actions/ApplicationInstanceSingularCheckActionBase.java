package com.phonepe.drove.executor.statemachine.application.actions;

import com.phonepe.drove.executor.checker.Checker;
import com.phonepe.drove.executor.statemachine.application.ApplicationInstanceAction;
import com.phonepe.drove.models.application.CheckResult;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.event.ExecutionCompletedEvent;
import net.jodah.failsafe.function.CheckedConsumer;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
@Slf4j
@NoArgsConstructor
public abstract class ApplicationInstanceSingularCheckActionBase extends ApplicationInstanceAction {
    protected final AtomicBoolean stop = new AtomicBoolean();

    @Override
    public final void stop() {
        stop.set(true);
    }

    @SuppressWarnings("java:S1874")
    protected final CheckResult checkWithRetry(
            RetryPolicy<CheckResult> retryPolicy,
            Checker checker,
            CheckedConsumer<ExecutionCompletedEvent<CheckResult>> errorConsumer) {
        return Failsafe.with(List.of(retryPolicy))
                .onComplete(errorConsumer)
                .get(() -> {
                    if (stop.get()) {
                        return CheckResult.stopped();
                    }
                    return checker.call();
                });
    }
}
