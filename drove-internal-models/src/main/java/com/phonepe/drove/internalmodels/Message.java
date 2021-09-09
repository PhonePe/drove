package com.phonepe.drove.internalmodels;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.phonepe.drove.internalmodels.executor.QueryInstanceMessage;
import com.phonepe.drove.internalmodels.executor.StartInstanceMessage;
import com.phonepe.drove.internalmodels.executor.StopInstanceMessage;
import lombok.Data;

/**
 *
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "START_INSTANCE", value = StartInstanceMessage.class),
        @JsonSubTypes.Type(name = "STOP_INSTANCE", value = StopInstanceMessage.class),
        @JsonSubTypes.Type(name = "QUERY_INSTANCE", value = QueryInstanceMessage.class),
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
