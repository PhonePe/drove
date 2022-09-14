package com.phonepe.drove.client;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotEmpty;
import java.time.Duration;
import java.util.List;

/**
 *
 */
@Data
@Jacksonized
@Builder
public class DroveClientConfig {
    @NotEmpty
    @Singular
    List<String> endpoints;

    Duration checkInterval;
    Duration connectionTimeout;
    Duration operationTimeout;
}
