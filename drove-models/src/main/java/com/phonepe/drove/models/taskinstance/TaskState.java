package com.phonepe.drove.models.taskinstance;

import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

/**
 *
 */
@Getter
public enum TaskState {
    PENDING(false, false),
    PROVISIONING(false, false),
    PROVISIONING_FAILED(false, true),
    STARTING(false, false),
    RUNNING(false, false),
    RUN_COMPLETED(false, false),
    DEPROVISIONING(false, false),
    STOPPED(true, false),
    LOST(true, false),
    UNKNOWN(false, false);

    public static final Set<TaskState> ACTIVE_STATES = Set.of(
            PENDING,
            PROVISIONING,
            STARTING,
            RUNNING,
            RUN_COMPLETED,
            DEPROVISIONING);

    public static final Set<TaskState> ALL = EnumSet.allOf(TaskState.class);

    private final boolean terminal;
    private final boolean error;

    TaskState(boolean terminal, boolean error) {
        this.terminal = terminal;
        this.error = error;
    }
}
