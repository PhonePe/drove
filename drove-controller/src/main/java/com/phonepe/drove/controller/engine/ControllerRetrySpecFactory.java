package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.retry.*;

import java.time.Duration;
import java.util.List;

/**
 *
 */
public interface ControllerRetrySpecFactory {
    default RetrySpec jobStartRetrySpec() {
        return new CompositeRetrySpec(
                List.of(new RetryOnAllExceptionsSpec(),
                        new MaxDurationRetrySpec(Duration.ofMinutes(3)),
                        new IntervalRetrySpec(Duration.ofSeconds(30))));
    }

    default RetrySpec instanceStateCheckRetrySpec(long timeoutMillis) {
        return new CompositeRetrySpec(
                List.of(
                new IntervalRetrySpec(Duration.ofSeconds(3)),
                new MaxRetriesRetrySpec(50),
                new MaxDurationRetrySpec(Duration.ofMillis(timeoutMillis)),
                new RetryOnAllExceptionsSpec()));
    }

    default RetrySpec appStateMachineRetrySpec() {
        return new CompositeRetrySpec(
                List.of(
                new IntervalRetrySpec(Duration.ofSeconds(3)),
                new MaxRetriesRetrySpec(50),
                new MaxDurationRetrySpec(Duration.ofSeconds(60)),
                new RetryOnAllExceptionsSpec()));
    }
}
