package com.phonepe.drove.jobexecutor;

/**
 *
 */
public interface JobResponseCombiner<T>{
    void combine(Job<T> job, final T newResponse);
    boolean handleError(final Throwable throwable);
    void handleCancel();
    T current();
    JobExecutionResult<T> buildResult(String jobId);
}
