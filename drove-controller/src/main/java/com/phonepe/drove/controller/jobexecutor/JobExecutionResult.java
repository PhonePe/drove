package com.phonepe.drove.controller.jobexecutor;

import lombok.Value;

/**
 *
 */
@Value
public class JobExecutionResult <T> {
    String jobId;
    T result;
    Throwable failure;
}
