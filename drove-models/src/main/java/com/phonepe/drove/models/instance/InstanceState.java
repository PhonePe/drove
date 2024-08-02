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

package com.phonepe.drove.models.instance;

import com.phonepe.drove.models.StateEnum;
import lombok.Getter;

import java.util.Set;

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
    LOST(true, false),
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
