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

/**
 * Controller fetches config by making HTTP calls and serve it to executor during spin-up
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ControllerHttpFetchConfigSpec extends ConfigSpec {
    HTTPCallSpec http;

    @Jacksonized
    @Builder
    public ControllerHttpFetchConfigSpec(
            String localFilename,
            HTTPCallSpec http) {
        super(ConfigSpecType.CONTROLLER_HTTP_FETCH, localFilename);
        this.http = http;
    }

    @Override
    public <T> T accept(ConfigSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
