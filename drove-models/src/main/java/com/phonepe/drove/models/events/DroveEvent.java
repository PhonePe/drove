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

package com.phonepe.drove.models.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.phonepe.drove.models.events.events.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 *
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
        @JsonSubTypes.Type(name = "EXECUTOR_BLACKLISTED", value = DroveExecutorBlacklistedEvent.class),
        @JsonSubTypes.Type(name = "EXECUTOR_UN_BLACKLISTED", value = DroveExecutorUnblacklistedEvent.class),
        @JsonSubTypes.Type(name = "MAINTENANCE_MODE_SET", value = DroveClusterMaintenanceModeSetEvent.class),
        @JsonSubTypes.Type(name = "MAINTENANCE_MODE_REMOVED", value = DroveClusterMaintenanceModeRemovedEvent.class),
        @JsonSubTypes.Type(name = "LEADERSHIP_ACQUIRED", value = DroveClusterLeadershipAcquiredEvent.class),
        @JsonSubTypes.Type(name = "LEADERSHIP_LOST", value = DroveClusterLeadershipLostEvent.class),
})
public abstract class DroveEvent<T extends Enum<T>> {
    private final DroveEventType type;
    private final String id = UUID.randomUUID().toString();
    private final Date time = new Date();
    private Map<T, Object> metadata;

    public static<T> MapBuilder<T, Object> metadataBuilder() {
        return new MapBuilder<>();
    }

    public abstract <U> U accept(final DroveEventVisitor<U> visitor);
}
