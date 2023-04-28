package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.retry.*;
import lombok.val;

import java.time.Duration;
import java.util.List;

/**
 *
 */
public interface ControllerRetrySpecFactory {
    default RetrySpec jobRetrySpec(long timeoutMillis) {
        val numRetries = 3;
        val timePerRetry = timeoutMillis + 100;
        return new CompositeRetrySpec(
                List.of(new RetryOnAllExceptionsSpec(),
                        new MaxRetriesRetrySpec(numRetries),
                        new MaxDurationRetrySpec(Duration.ofMillis(numRetries * timePerRetry + 100)),
                        new IntervalRetrySpec(Duration.ofMillis(timePerRetry))));
    }

    default RetrySpec instanceStateCheckRetrySpec(long timeoutMillis) {
        return new CompositeRetrySpec(
                List.of(
                new IntervalRetrySpec(Duration.ofSeconds(3)),
                new MaxRetriesRetrySpec(-1),
                new MaxDurationRetrySpec(Duration.ofMillis(timeoutMillis)),
                new RetryOnAllExceptionsSpec()));
    }

    default RetrySpec appStateMachineRetrySpec() {
        return new CompositeRetrySpec(
                List.of(
                new IntervalRetrySpec(Duration.ofSeconds(3)),
                new MaxRetriesRetrySpec(-1),
                new MaxDurationRetrySpec(Duration.ofSeconds(60)),
                new RetryOnAllExceptionsSpec()));
    }
}
