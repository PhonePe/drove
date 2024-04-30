package com.phonepe.drove.models.config.impl;

import com.phonepe.drove.models.common.HTTPCallSpec;
import com.phonepe.drove.models.config.ConfigSpec;
import com.phonepe.drove.models.config.ConfigSpecType;
import com.phonepe.drove.models.config.ConfigSpecVisitor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotNull;

/**
 * Executor fetches config by making HTTP calls at container startup
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExecutorHttpFetchConfigSpec extends ConfigSpec {
    @NotNull
    HTTPCallSpec http;

    @Jacksonized
    @Builder
    public ExecutorHttpFetchConfigSpec(
            String localFilename,
            HTTPCallSpec http) {
        super(ConfigSpecType.EXECUTOR_HTTP_FETCH, localFilename);
        this.http = http;
    }

    @Override
    public <T> T accept(ConfigSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
