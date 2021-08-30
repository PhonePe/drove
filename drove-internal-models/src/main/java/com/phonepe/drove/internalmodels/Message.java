package com.phonepe.drove.internalmodels;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

/**
 *
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@Data
public abstract class Message<T extends Enum<T>> {
    private final T type;
    private final MessageHeader header;
    protected Message(T type, MessageHeader header) {
        this.type = type;
        this.header = header;
    }
}
