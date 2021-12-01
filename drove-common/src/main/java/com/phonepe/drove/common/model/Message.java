package com.phonepe.drove.common.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.phonepe.drove.common.model.controller.ExecutorSnapshotMessage;
import com.phonepe.drove.common.model.controller.ExecutorStateReportMessage;
import com.phonepe.drove.common.model.controller.InstanceStateReportMessage;
import com.phonepe.drove.common.model.executor.*;
import lombok.Data;

/**
 *
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "START_INSTANCE", value = StartInstanceMessage.class),
        @JsonSubTypes.Type(name = "STOP_INSTANCE", value = StopInstanceMessage.class),
        @JsonSubTypes.Type(name = "BLACKLIST", value = BlacklistExecutorMessage.class),
        @JsonSubTypes.Type(name = "UNBLACKLIST", value = UnBlacklistExecutorMessage.class),
        @JsonSubTypes.Type(name = "INSTANCE_STATE_REPORT", value = InstanceStateReportMessage.class),
        @JsonSubTypes.Type(name = "EXECUTOR_STATE_REPORT", value = ExecutorStateReportMessage.class),
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
