package com.phonepe.drove.models.taskinstance;

import lombok.Value;

/**
 *
 */
@Value
public class TaskResult {
    public enum Status {
        SUCCESSFUL,
        TIMED_OUT,
        CANCELLED,
        FAILED,
        LOST
    }

    Status status;
    long exitCode;
}
