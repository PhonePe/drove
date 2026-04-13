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

package com.phonepe.drove.models.taskinstance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

/**
 * Task lifecycle states
 */
@Getter
@Schema(description = "Task lifecycle state")
public enum TaskState {
    @Schema(description = "Task is pending scheduling")
    PENDING(false, false),

    @Schema(description = "Task is being provisioned on an executor")
    PROVISIONING(false, false),

    @Schema(description = "Task provisioning failed")
    PROVISIONING_FAILED(false, true),

    @Schema(description = "Task container is starting")
    STARTING(false, false),

    @Schema(description = "Task is running")
    RUNNING(false, false),

    @Schema(description = "Task run completed successfully")
    RUN_COMPLETED(false, false),

    @Schema(description = "Task run failed")
    RUN_FAILED(false, true),

    @Schema(description = "Task is being deprovisioned")
    DEPROVISIONING(false, false),

    @Schema(description = "Task has stopped (terminal state)")
    STOPPED(true, false),

    @Schema(description = "Task connection was lost (terminal state)")
    LOST(true, false),

    @Schema(description = "Task state is unknown")
    UNKNOWN(false, false);

    public static final Set<TaskState> ACTIVE_STATES = Set.of(
            PENDING,
            PROVISIONING,
            STARTING,
            RUNNING,
            RUN_COMPLETED,
            DEPROVISIONING);

    public static final Set<TaskState> ALL = Set.copyOf(EnumSet.allOf(TaskState.class));

    private final boolean terminal;
    private final boolean error;

    TaskState(boolean terminal, boolean error) {
        this.terminal = terminal;
        this.error = error;
    }
}
