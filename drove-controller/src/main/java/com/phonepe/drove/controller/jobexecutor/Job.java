package com.phonepe.drove.controller.jobexecutor;

/**
 *
 */
public interface Job<T> {

    String jobId();

    void cancel();

    T execute(
            JobContext context,
            final JobResponseCombiner<T> responseCombiner);

    default boolean isBatch() {
        return false;
    }
}
