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

package com.phonepe.drove.models.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.Set;

/**
 * Application lifecycle states
 */
@Schema(description = "Application lifecycle state")
public enum ApplicationState {
    @Schema(description = "Application is being initialized")
    INIT(false),

    @Schema(description = "Application is being monitored for health")
    MONITORING(false),

    @Schema(description = "Application is running normally with healthy instances")
    RUNNING(false),

    @Schema(description = "Outage detected - some instances are unhealthy")
    OUTAGE_DETECTED(false),

    @Schema(description = "Scaling operation has been requested")
    SCALING_REQUESTED(false),

    @Schema(description = "Instance stop operation has been requested")
    STOP_INSTANCES_REQUESTED(false),

    @Schema(description = "Instance replacement operation has been requested")
    REPLACE_INSTANCES_REQUESTED(false),

    @Schema(description = "Application destruction has been requested")
    DESTROY_REQUESTED(false),

    @Schema(description = "Application has been destroyed (terminal state)")
    DESTROYED(true),

    @Schema(description = "Application has failed (terminal state)")
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
