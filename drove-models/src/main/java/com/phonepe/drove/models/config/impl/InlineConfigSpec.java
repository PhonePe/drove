package com.phonepe.drove.models.config.impl;

import com.phonepe.drove.models.common.Mask;
import com.phonepe.drove.models.config.ConfigSpec;
import com.phonepe.drove.models.config.ConfigSpecType;
import com.phonepe.drove.models.config.ConfigSpecVisitor;
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
@ToString
public class InlineConfigSpec extends ConfigSpec {

    @Mask
    @NotEmpty
    byte []data;

    @Jacksonized
    @Builder
    public InlineConfigSpec(String localFilename, byte[] data) {
        super(ConfigSpecType.INLINE, localFilename);
        this.data = data;
    }

    @Override
    public <T> T accept(ConfigSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}