package com.phonepe.drove.models.api;

import lombok.Value;

import java.util.Collection;

/**
 *
 */
@Value
public class ExposedAppInfo {
    @Value
    public static class ExposedHost {
        String host;
        int port;
    }
    String appId;
    String vhost;
    Collection<ExposedHost> hosts;
}
