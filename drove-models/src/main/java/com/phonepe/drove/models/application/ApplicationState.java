/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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
