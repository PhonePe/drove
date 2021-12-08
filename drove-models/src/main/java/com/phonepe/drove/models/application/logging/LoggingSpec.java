package com.phonepe.drove.models.application.logging;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

/**
 *
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "LOCAL", value = LocalLoggingSpec.class),
        @JsonSubTypes.Type(name = "RSYSLOG", value = RsyslogLoggingSpec.class)
})
@Data
public abstract class LoggingSpec {
    private final LoggingType type;

    protected LoggingSpec(LoggingType type) {
        this.type = type;
    }

    public abstract <T> T accept(final LoggingSpecVisitor<T> visitor);
}
