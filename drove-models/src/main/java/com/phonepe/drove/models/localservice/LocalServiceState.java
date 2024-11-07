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

package com.phonepe.drove.models.localservice;

import lombok.Getter;

import java.util.Set;

/**
 * State of a service running on executor
 */
@Getter
public enum LocalServiceState {
    INIT(false),
    ACTIVATION_REQUESTED(false),
    DEACTIVATION_REQUESTED(false),
    INACTIVE(false),
    ACTIVE(false),
    SCALING(false),
    RESTARTING(false),
    STOP_INSTANCES_REQUESTED(false),
    DESTROY_REQUESTED(false),
    DESTROYED(true);

    public static final Set<LocalServiceState> ACTIVE_STATES = Set.of(ACTIVE);

    private final boolean terminal;

    LocalServiceState(boolean terminal) {
        this.terminal = terminal;
    }
}
