package com.phonepe.drove.models.application;

import lombok.Getter;

/**
 *
 */
public enum ApplicationState {
    INIT(false),
    CREATED(false),
    DEPLOYMENT_REQUESTED(false),
    RUNNING(false),
    PARTIAL_OUTAGE(false),
    SUSPEND_REQUESTED(false),
    SCALING_REQUESTED(false),
    RESTART_REQUESTED(false),
    DESTROY_REQUESTED(false),
    DOWN(false),
    SUSPENDED(true),
    FAILED(true);

    @Getter
    private final boolean terminal;

    ApplicationState(boolean terminal) {
        this.terminal = terminal;
    }
}
