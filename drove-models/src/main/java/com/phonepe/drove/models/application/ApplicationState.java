package com.phonepe.drove.models.application;

import lombok.Getter;

/**
 *
 */
public enum ApplicationState {
    INIT(false),
    MONITORING(false),
    RUNNING(false),
    OUTAGE_DETECTED(false),
    SCALING_REQUESTED(false),
    STOP_INSTANCES_REQUESTED(false),
    RESTART_REQUESTED(false),
    DESTROY_REQUESTED(false),
    DESTROYED(true),
    FAILED(true);

    @Getter
    private final boolean terminal;

    ApplicationState(boolean terminal) {
        this.terminal = terminal;
    }
}
