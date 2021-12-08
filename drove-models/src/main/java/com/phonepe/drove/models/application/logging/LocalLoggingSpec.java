package com.phonepe.drove.models.application.logging;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
public class LocalLoggingSpec extends LoggingSpec {
    public static final LoggingSpec DEFAULT = new LocalLoggingSpec("10m", 3, true);

    @NotEmpty
    String maxSize;

    @Min(1)
    @Max(100)
    int maxFiles;
    boolean compress;

    @Builder
    public LocalLoggingSpec(String maxSize, int maxFiles, boolean compress) {
        super(LoggingType.LOCAL);
        this.maxSize = maxSize;
        this.maxFiles = maxFiles;
        this.compress = compress;
    }

    @Override
    public <T> T accept(LoggingSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
