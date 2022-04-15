package com.phonepe.drove.models.application.logging;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotEmpty;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
public class RsyslogLoggingSpec extends LoggingSpec {
    @NotEmpty(message = "- Rsyslog server url should be passed here")
    String server;

    String tagPrefix;

    String tagSuffix;


    @Builder
    public RsyslogLoggingSpec(String server, String tagPrefix, String tagSuffix) {
        super(LoggingType.RSYSLOG);
        this.server = server;
        this.tagPrefix = tagPrefix;
        this.tagSuffix = tagSuffix;
    }

    @Override
    public <T> T accept(LoggingSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
