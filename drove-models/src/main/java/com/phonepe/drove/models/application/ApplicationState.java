package com.phonepe.drove.models.application;

import lombok.Getter;

import java.util.Set;

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
    REPLACE_INSTANCES_REQUESTED(false),
    DESTROY_REQUESTED(false),
    DESTROYED(true),
    FAILED(true);

    public static final Set<ApplicationState> ACTIVE_APP_STATES = Set.of(RUNNING,
                                                                         OUTAGE_DETECTED,
                                                                         SCALING_REQUESTED,
                                                                         STOP_INSTANCES_REQUESTED,
                                                                         REPLACE_INSTANCES_REQUESTED,
                                                                         DESTROY_REQUESTED);

    @Getter
    private final boolean terminal;

    ApplicationState(boolean terminal) {
        this.terminal = terminal;
    }
}
