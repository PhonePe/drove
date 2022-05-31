package com.phonepe.drove.jobexecutor;

/**
 *
 */
public interface Job<T> {

    String jobId();

    void cancel();

    T execute(
            JobContext<T> context,
            final JobResponseCombiner<T> responseCombiner);

    default boolean isBatch() {
        return false;
    }
}
