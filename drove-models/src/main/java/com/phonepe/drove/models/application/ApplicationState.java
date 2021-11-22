package com.phonepe.drove.models.application;

import lombok.Getter;

/**
 *
 */
public enum ApplicationState {
    INIT(false),
    MONITORING(false),
    DEPLOYMENT_REQUESTED(false),
    RUNNING(false),
    OUTAGE_DETECTED(false),
    SUSPEND_REQUESTED(false),
    SCALING_REQUESTED(false),
    STOP_INSTANCES_REQUESTED(false),
    RESTART_REQUESTED(false),
    DESTROY_REQUESTED(false),
    DOWN(false),
    DESTROYED(true),
    FAILED(true);

    @Getter
    private final boolean terminal;

    ApplicationState(boolean terminal) {
        this.terminal = terminal;
    }
}
