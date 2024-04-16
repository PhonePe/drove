package com.phonepe.drove.models.config.impl;

import com.phonepe.drove.models.config.ConfigSpec;
import com.phonepe.drove.models.config.ConfigSpecType;
import com.phonepe.drove.models.config.ConfigSpecVisitor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * File will be mounted from a path on executor directly
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString
public class ExecutorLocalFileConfigSpec extends ConfigSpec {

    String filePathOnHost;

    @Jacksonized
    @Builder
    public ExecutorLocalFileConfigSpec(String localFilename, String filePathOnHost) {
        super(ConfigSpecType.EXECUTOR_LOCAL_FILE, localFilename);
        this.filePathOnHost = filePathOnHost;
    }

    @Override
    public <T> T accept(ConfigSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
