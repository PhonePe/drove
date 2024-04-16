package com.phonepe.drove.models.config;

/**
 * Source of configuration for the application or task instance
 */
public enum ConfigSpecType {
    // Sent as part of spec
    INLINE,
    // Locally mounted file
    EXECUTOR_LOCAL_FILE,
    // Config is fetched by controller using http call and sent to instance
    CONTROLLER_HTTP_FETCH,
    // Config is fetched by executor directly by making http call
    EXECUTOR_HTTP_FETCH
}
