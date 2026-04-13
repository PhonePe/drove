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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.Set;

/**
 * State of a local service running on executor nodes.
 */
@Getter
@Schema(description = "State of a local service in the cluster lifecycle")
public enum LocalServiceState {
    @Schema(description = "Service is being initialized")
    INIT(false),
    @Schema(description = "Activation has been requested")
    ACTIVATION_REQUESTED(false),
    @Schema(description = "Configuration testing has been requested")
    CONFIG_TESTING_REQUESTED(false),
    @Schema(description = "Deactivation has been requested")
    DEACTIVATION_REQUESTED(false),
    @Schema(description = "Emergency deactivation has been requested due to issues")
    EMERGENCY_DEACTIVATION_REQUESTED(false),
    @Schema(description = "Service is inactive (not running)")
    INACTIVE(false),
    @Schema(description = "Service is active and running")
    ACTIVE(false),
    @Schema(description = "Service is running in configuration test mode")
    CONFIG_TESTING(false),
    @Schema(description = "Service is adjusting its instance count")
    ADJUSTING_INSTANCES(false),
    @Schema(description = "A test instance is being deployed")
    DEPLOYING_TEST_INSTANCE(false),
    @Schema(description = "Service instances are being replaced")
    REPLACING_INSTANCES(false),
    @Schema(description = "Service instances are being stopped")
    STOPPING_INSTANCES(false),
    @Schema(description = "Instance count is being updated")
    UPDATING_INSTANCES_COUNT(false),
    @Schema(description = "Service destruction has been requested")
    DESTROY_REQUESTED(false),
    @Schema(description = "Service has been destroyed (terminal state)")
    DESTROYED(true);

    public static final Set<LocalServiceState> RESOURCE_USING_STATES = Set.of(ACTIVE, CONFIG_TESTING);

    private final boolean terminal;

    LocalServiceState(boolean terminal) {
        this.terminal = terminal;
    }
}
