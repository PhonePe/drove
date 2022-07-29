package com.phonepe.drove.common.retry;

import lombok.Value;

import java.time.Duration;

/**
 *
 */
@Value
public class IntervalRetrySpec implements RetrySpec {
    Duration interval;

    @Override
    public <T> T accept(RetrySpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
