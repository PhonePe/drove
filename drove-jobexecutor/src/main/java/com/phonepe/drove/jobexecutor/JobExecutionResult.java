package com.phonepe.drove.jobexecutor;

import lombok.Value;

/**
 *
 */
@Value
public class JobExecutionResult <T> {
    String jobId;
    T result;
    Throwable failure;
    boolean cancelled;
}
