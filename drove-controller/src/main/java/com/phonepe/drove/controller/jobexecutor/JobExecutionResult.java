package com.phonepe.drove.controller.jobexecutor;

import lombok.Value;

/**
 *
 */
@Value
public class JobExecutionResult <T> {
    T result;
    Throwable failure;
}
