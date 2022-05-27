package com.phonepe.drove.common.retry;

import lombok.Value;

import java.time.Duration;

/**
 *
 */
@Value
public class MaxDurationRetrySpec implements RetrySpec {
    Duration maxDuration;

    @Override
    public <T> T accept(RetrySpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
