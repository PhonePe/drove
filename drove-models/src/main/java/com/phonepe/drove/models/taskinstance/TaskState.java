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

    public static final Set<TaskState> ALL = Set.copyOf(EnumSet.allOf(TaskState.class));

    private final boolean terminal;
    private final boolean error;

    TaskState(boolean terminal, boolean error) {
        this.terminal = terminal;
        this.error = error;
    }
}
