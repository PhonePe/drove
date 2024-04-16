package com.phonepe.drove.models.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.phonepe.drove.models.config.impl.ControllerHttpFetchConfigSpec;
import com.phonepe.drove.models.config.impl.ExecutorHttpFetchConfigSpec;
import com.phonepe.drove.models.config.impl.ExecutorLocalFileConfigSpec;
import com.phonepe.drove.models.config.impl.InlineConfigSpec;
import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import javax.validation.constraints.NotEmpty;

/**
 * Specification for configuration
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "INLINE", value = InlineConfigSpec.class),
        @JsonSubTypes.Type(name = "EXECUTOR_LOCAL_FILE", value = ExecutorLocalFileConfigSpec.class),
        @JsonSubTypes.Type(name = "CONTROLLER_HTTP_FETCH", value = ControllerHttpFetchConfigSpec.class),
        @JsonSubTypes.Type(name = "EXECUTOR_HTTP_FETCH", value = ExecutorHttpFetchConfigSpec.class),
})
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
@Data
public abstract class ConfigSpec {

    private final ConfigSpecType type;

    @NotEmpty
    private final String localFilename;

    public abstract <T> T accept(final ConfigSpecVisitor<T> visitor);
}
