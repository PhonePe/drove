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

package com.phonepe.drove.models.events.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.phonepe.drove.models.events.DroveEventType;
import com.phonepe.drove.models.events.events.datatags.ExecutorEventDataTag;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import java.util.Map;

/**
 * An executor is added to the cluster
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DroveExecutorRemovedEvent extends DroveExecutorEvent {
    public DroveExecutorRemovedEvent(@JsonProperty("metadata") Map<ExecutorEventDataTag, Object> metadata) {
        super(DroveEventType.EXECUTOR_REMOVED, metadata);
    }

    @Override
    public <T> T accept(DroveEventVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
