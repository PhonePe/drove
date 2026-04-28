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

package com.phonepe.drove.models.events;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Types of events emitted by the Drove cluster
 */
@Schema(description = "Type of event emitted by the Drove cluster")
public enum DroveEventType {
    @Schema(description = "Application state has changed")
    APP_STATE_CHANGE,
    @Schema(description = "Application instance state has changed")
    INSTANCE_STATE_CHANGE,
    @Schema(description = "Task instance state has changed")
    TASK_STATE_CHANGE,
    @Schema(description = "Local service state has changed")
    LOCAL_SERVICE_STATE_CHANGE,
    @Schema(description = "Local service instance state has changed")
    LOCAL_SERVICE_INSTANCE_STATE_CHANGE,
    @Schema(description = "New executor node added to the cluster")
    EXECUTOR_ADDED,
    @Schema(description = "Executor node removed from the cluster")
    EXECUTOR_REMOVED,
    @Schema(description = "Executor node has been requested to be blacklisted")
    EXECUTOR_BLACKLIST_REQUESTED,
    @Schema(description = "Executor node has been blacklisted")
    EXECUTOR_BLACKLISTED,
    @Schema(description = "Executor node has been un-blacklisted")
    EXECUTOR_UN_BLACKLISTED,
    @Schema(description = "Cluster has entered maintenance mode")
    MAINTENANCE_MODE_SET,
    @Schema(description = "Cluster has exited maintenance mode")
    MAINTENANCE_MODE_REMOVED,

    @Schema(description = "Controller has acquired leadership")
    LEADERSHIP_ACQUIRED,
    @Schema(description = "Controller has lost leadership")
    LEADERSHIP_LOST
}
