package com.phonepe.drove.auth.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 *
 */
@Value
@Jacksonized
@Builder
@AllArgsConstructor
public class ApplicationAuthConfig {
    public static final ApplicationAuthConfig DEFAULT = new ApplicationAuthConfig("DEFAULT_SECRET");

    String secret;
}
