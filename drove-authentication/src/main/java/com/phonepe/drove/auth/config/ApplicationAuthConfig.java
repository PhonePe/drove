package com.phonepe.drove.auth.config;

import lombok.Value;

/**
 *
 */
@Value
public class ApplicationAuthConfig {
    public static final ApplicationAuthConfig DEFAULT = new ApplicationAuthConfig("DEFAULT_SECRET");

    String secret;
}
