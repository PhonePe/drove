/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.models.instance;

import com.phonepe.drove.models.StateEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.Set;

/**
 * Instance lifecycle states
 */
@Getter
@Schema(description = "Instance lifecycle state")
public enum InstanceState implements StateEnum {
    @Schema(description = "Instance is pending scheduling")
    PENDING(false, false),

    @Schema(description = "Instance is being provisioned on an executor")
    PROVISIONING(false, false),

    @Schema(description = "Provisioning failed")
    PROVISIONING_FAILED(false, true),

    @Schema(description = "Instance container is starting")
    STARTING(false, false),

    @Schema(description = "Container start failed")
    START_FAILED(false, true),

    @Schema(description = "Instance is running but not yet ready")
    UNREADY(false, false),

    @Schema(description = "Readiness check failed")
    READINESS_CHECK_FAILED(false, true),

    @Schema(description = "Instance is ready to receive traffic")
    READY(false, false),

    @Schema(description = "Instance is healthy and serving traffic")
    HEALTHY(false, false),

    @Schema(description = "Instance health check failed")
    UNHEALTHY(false, false),

    @Schema(description = "Instance is being deprovisioned")
    DEPROVISIONING(false, false),

    @Schema(description = "Instance is stopping")
    STOPPING(false, false),

    @Schema(description = "Instance has stopped (terminal state)")
    STOPPED(true, false),

    @Schema(description = "Instance connection was lost (terminal state)")
    LOST(true, false),

    @Schema(description = "Instance state is unknown")
    UNKNOWN(false, false);

    public static final Set<InstanceState> ACTIVE_STATES = Set.of(
            PENDING,
            PROVISIONING,
            STARTING,
            UNREADY,
            READY,
            HEALTHY,
            UNHEALTHY,
            DEPROVISIONING,
            STOPPING);

    public static final Set<InstanceState> RUNNING_STATES = Set.of(UNREADY, READY, HEALTHY);

    private final boolean terminal;
    private final boolean error;

    InstanceState(boolean terminal, boolean error) {
        this.terminal = terminal;
        this.error = error;
    }
}
