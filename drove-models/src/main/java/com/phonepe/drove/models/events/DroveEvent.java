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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.phonepe.drove.models.events.events.*;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for all Drove events
 */
@Data
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "APP_STATE_CHANGE", value = DroveAppStateChangeEvent.class),
        @JsonSubTypes.Type(name = "INSTANCE_STATE_CHANGE", value = DroveInstanceStateChangeEvent.class),
        @JsonSubTypes.Type(name = "TASK_STATE_CHANGE", value = DroveTaskStateChangeEvent.class),
        @JsonSubTypes.Type(name = "EXECUTOR_ADDED", value = DroveExecutorAddedEvent.class),
        @JsonSubTypes.Type(name = "EXECUTOR_REMOVED", value = DroveExecutorRemovedEvent.class),
        @JsonSubTypes.Type(name = "EXECUTOR_BLACKLIST_REQUESTED", value = DroveExecutorBlacklistRequestedEvent.class),
        @JsonSubTypes.Type(name = "EXECUTOR_BLACKLISTED", value = DroveExecutorBlacklistedEvent.class),
        @JsonSubTypes.Type(name = "EXECUTOR_UN_BLACKLISTED", value = DroveExecutorUnblacklistedEvent.class),
        @JsonSubTypes.Type(name = "MAINTENANCE_MODE_SET", value = DroveClusterMaintenanceModeSetEvent.class),
        @JsonSubTypes.Type(name = "MAINTENANCE_MODE_REMOVED", value = DroveClusterMaintenanceModeRemovedEvent.class),
        @JsonSubTypes.Type(name = "LEADERSHIP_ACQUIRED", value = DroveClusterLeadershipAcquiredEvent.class),
        @JsonSubTypes.Type(name = "LEADERSHIP_LOST", value = DroveClusterLeadershipLostEvent.class),
})
@Schema(
    description = "Event emitted by the Drove cluster for state changes, executor updates, and cluster operations",
    discriminatorProperty = "type",
    discriminatorMapping = {
        @DiscriminatorMapping(value = "APP_STATE_CHANGE", schema = DroveAppStateChangeEvent.class),
        @DiscriminatorMapping(value = "INSTANCE_STATE_CHANGE", schema = DroveInstanceStateChangeEvent.class),
        @DiscriminatorMapping(value = "TASK_STATE_CHANGE", schema = DroveTaskStateChangeEvent.class),
        @DiscriminatorMapping(value = "EXECUTOR_ADDED", schema = DroveExecutorAddedEvent.class),
        @DiscriminatorMapping(value = "EXECUTOR_REMOVED", schema = DroveExecutorRemovedEvent.class),
        @DiscriminatorMapping(value = "EXECUTOR_BLACKLISTED", schema = DroveExecutorBlacklistedEvent.class),
        @DiscriminatorMapping(value = "EXECUTOR_UN_BLACKLISTED", schema = DroveExecutorUnblacklistedEvent.class),
        @DiscriminatorMapping(value = "MAINTENANCE_MODE_SET", schema = DroveClusterMaintenanceModeSetEvent.class),
        @DiscriminatorMapping(value = "MAINTENANCE_MODE_REMOVED", schema = DroveClusterMaintenanceModeRemovedEvent.class),
        @DiscriminatorMapping(value = "LEADERSHIP_ACQUIRED", schema = DroveClusterLeadershipAcquiredEvent.class),
        @DiscriminatorMapping(value = "LEADERSHIP_LOST", schema = DroveClusterLeadershipLostEvent.class)
    },
    subTypes = {
        DroveAppStateChangeEvent.class,
        DroveInstanceStateChangeEvent.class,
        DroveTaskStateChangeEvent.class,
        DroveExecutorAddedEvent.class,
        DroveExecutorRemovedEvent.class,
        DroveExecutorBlacklistedEvent.class,
        DroveExecutorUnblacklistedEvent.class,
        DroveClusterMaintenanceModeSetEvent.class,
        DroveClusterMaintenanceModeRemovedEvent.class,
        DroveClusterLeadershipAcquiredEvent.class,
        DroveClusterLeadershipLostEvent.class
    }
)
public abstract class DroveEvent<T extends Enum<T>> {
    @Schema(description = "Type of event", requiredMode = Schema.RequiredMode.REQUIRED)
    private final DroveEventType type;

    @Schema(description = "Unique identifier for this event", example = "550e8400-e29b-41d4-a716-446655440000")
    private final String id = UUID.randomUUID().toString();

    @Schema(description = "Timestamp when the event occurred")
    private final Date time = new Date();

    @Schema(description = "Event-specific metadata as key-value pairs")
    private Map<T, Object> metadata;

    public static<T> MapBuilder<T, Object> metadataBuilder() {
        return new MapBuilder<>();
    }

    public abstract <U> U accept(final DroveEventVisitor<U> visitor);
}
