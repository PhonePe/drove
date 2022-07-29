package com.phonepe.drove.common.retry;

import lombok.Value;

import java.util.List;

/**
 *
 */
@Value
public class CompositeRetrySpec implements RetrySpec {
    List<RetrySpec> specs;

    @Override
    public <T> T accept(RetrySpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
