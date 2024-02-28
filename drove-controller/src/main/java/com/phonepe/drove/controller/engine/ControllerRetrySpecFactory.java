package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.retry.*;

import java.time.Duration;
import java.util.List;

/**
 *
 */
public interface ControllerRetrySpecFactory {

    RetrySpec jobRetrySpec();

    RetrySpec instanceStateCheckRetrySpec(long timeoutMillis);

    default RetrySpec appStateMachineRetrySpec() {
        return new CompositeRetrySpec(
                List.of(
                new IntervalRetrySpec(Duration.ofSeconds(3)),
                new MaxRetriesRetrySpec(-1),
                new MaxDurationRetrySpec(Duration.ofSeconds(60)),
                new RetryOnAllExceptionsSpec()));
    }
}
