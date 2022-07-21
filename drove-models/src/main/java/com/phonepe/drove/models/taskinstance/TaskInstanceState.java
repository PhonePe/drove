package com.phonepe.drove.models.taskinstance;

import lombok.Getter;

/**
 *
 */
@Getter
public enum TaskInstanceState {
    PENDING(false, false),
    PROVISIONING(false, false),
    PROVISIONING_FAILED(false, true),
    STARTING(false, false),
    START_FAILED(false, true),
    RUNNING(false, false),
    RUN_FAILED(false, true),
    RUN_TIMEOUT(false, true),
    RUN_COMPLETED(false, false),
    RUN_CANCELLED(false, false),
    DEPROVISIONING(false, false),
    STOPPING(false, false),
    STOPPED(true, false),
    LOST(true, false),
    UNKNOWN(false, false);

    private final boolean terminal;
    private final boolean error;

    TaskInstanceState(boolean terminal, boolean error) {
        this.terminal = terminal;
        this.error = error;
    }
}
