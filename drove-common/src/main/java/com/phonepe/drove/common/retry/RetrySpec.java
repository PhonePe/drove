package com.phonepe.drove.common.retry;

/**
 *
 */
public interface RetrySpec {
    <T> T accept(final RetrySpecVisitor<T> visitor);
}
