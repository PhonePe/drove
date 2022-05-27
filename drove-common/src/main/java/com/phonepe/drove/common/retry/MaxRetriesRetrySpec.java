package com.phonepe.drove.common.retry;

import lombok.Value;

/**
 *
 */
@Value
public class MaxRetriesRetrySpec implements RetrySpec {
    int maxRetries;

    @Override
    public <T> T accept(RetrySpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
