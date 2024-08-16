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

package com.phonepe.drove.common.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.phonepe.drove.common.model.controller.ExecutorSnapshotMessage;
import com.phonepe.drove.common.model.controller.InstanceStateReportMessage;
import com.phonepe.drove.common.model.controller.TaskStateReportMessage;
import com.phonepe.drove.common.model.executor.*;
import lombok.Data;

/**
 *
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "START_INSTANCE", value = StartInstanceMessage.class),
        @JsonSubTypes.Type(name = "STOP_INSTANCE", value = StopInstanceMessage.class),
        @JsonSubTypes.Type(name = "START_TASK", value = StartTaskMessage.class),
        @JsonSubTypes.Type(name = "STOP_TASK", value = StopTaskMessage.class),
        @JsonSubTypes.Type(name = "BLACKLIST", value = BlacklistExecutorMessage.class),
        @JsonSubTypes.Type(name = "UNBLACKLIST", value = UnBlacklistExecutorMessage.class),
        @JsonSubTypes.Type(name = "INSTANCE_STATE_REPORT", value = InstanceStateReportMessage.class),
        @JsonSubTypes.Type(name = "TASK_STATE_REPORT", value = TaskStateReportMessage.class),
        @JsonSubTypes.Type(name = "EXECUTOR_SNAPSHOT", value = ExecutorSnapshotMessage.class),
})
@Data
public abstract class Message<T extends Enum<T>> {
    private final T type;
    private final MessageHeader header;
    protected Message(T type, MessageHeader header) {
        this.type = type;
        this.header = header;
    }
}
