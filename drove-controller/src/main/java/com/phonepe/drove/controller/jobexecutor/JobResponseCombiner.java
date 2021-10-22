package com.phonepe.drove.controller.jobexecutor;

/**
 *
 */
public interface JobResponseCombiner<T>{
    void combine(Job<T> job, final T newResponse);
    boolean handleError(final Throwable throwable);
    T current();
    JobExecutionResult<T> buildResult();
}
