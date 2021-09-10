package com.phonepe.drove.models.instance;

import com.phonepe.drove.models.StateEnum;
import lombok.Getter;

/**
 *
 */
@Getter
public enum InstanceState implements StateEnum {
    PENDING(false, false),
    PROVISIONING(false, false),
    PROVISIONING_FAILED(false, true),
    STARTING(false, false),
    START_FAILED(false, true),
    UNREADY(false, false),
    READINESS_CHECK_FAILED(false, true),
    READY(false, false),
    HEALTHY(false, false),
    UNHEALTHY(false, false),
    DEPROVISIONING(false, false),
    STOPPING(false, false),
    STOPPED(true, false),
    TERMINATED(false, false),
    UNREACHABLE(false, false),
    UNKNOWN(false, false);

    private final boolean terminal;
    private final boolean error;

    InstanceState(boolean terminal, boolean error) {
        this.terminal = terminal;
        this.error = error;
    }
}
