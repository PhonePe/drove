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
 * State of a local service instance in its lifecycle.
 */
@Getter
@Schema(description = "State of a local service instance in its lifecycle")
public enum LocalServiceInstanceState implements StateEnum {
    @Schema(description = "Instance is pending creation")
    PENDING(false, false),
    @Schema(description = "Instance is being provisioned")
    PROVISIONING(false, false),
    @Schema(description = "Instance provisioning failed")
    PROVISIONING_FAILED(false, true),
    @Schema(description = "Instance is starting")
    STARTING(false, false),
    @Schema(description = "Instance failed to start")
    START_FAILED(false, true),
    @Schema(description = "Instance started but not ready yet")
    UNREADY(false, false),
    @Schema(description = "Instance readiness check failed")
    READINESS_CHECK_FAILED(false, true),
    @Schema(description = "Instance is ready to serve traffic")
    READY(false, false),
    @Schema(description = "Instance is healthy")
    HEALTHY(false, false),
    @Schema(description = "Instance is unhealthy")
    UNHEALTHY(false, false),
    @Schema(description = "Instance is being deprovisioned")
    DEPROVISIONING(false, false),
    @Schema(description = "Instance is stopping")
    STOPPING(false, false),
    @Schema(description = "Instance has stopped (terminal state)")
    STOPPED(true, false),
    @Schema(description = "Instance was lost (terminal state)")
    LOST(true, false),
    @Schema(description = "Instance state is unknown")
    UNKNOWN(false, false);

    public static final Set<LocalServiceInstanceState> ACTIVE_STATES = Set.of(
            PENDING,
            PROVISIONING,
            STARTING,
            UNREADY,
            READY,
            HEALTHY,
            UNHEALTHY,
            DEPROVISIONING,
            STOPPING);

    public static final Set<LocalServiceInstanceState> RUNNING_STATES = Set.of(UNREADY, READY, HEALTHY);

    private final boolean terminal;
    private final boolean error;

    LocalServiceInstanceState(boolean terminal, boolean error) {
        this.terminal = terminal;
        this.error = error;
    }
}
