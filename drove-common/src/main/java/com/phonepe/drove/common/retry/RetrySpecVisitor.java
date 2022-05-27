package com.phonepe.drove.common.retry;

/**
 *
 */
public interface RetrySpecVisitor<T> {
    T visit(final CompositeRetrySpec composite);

    T visit(final IntervalRetrySpec interval);

    T visit(final MaxDurationRetrySpec maxDuration);

    T visit(final MaxRetriesRetrySpec maxRetries);

    T visit(final RetryOnAllExceptionsSpec exceptionRetry);


}
