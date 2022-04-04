package com.phonepe.drove.common.retry;

import lombok.Value;

/**
 *
 */
@Value
public class RetryOnAllExceptionsSpec implements RetrySpec {

    @Override
    public <T> T accept(RetrySpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
