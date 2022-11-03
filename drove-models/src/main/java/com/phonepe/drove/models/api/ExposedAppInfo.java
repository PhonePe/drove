package com.phonepe.drove.models.api;

import com.phonepe.drove.models.application.PortType;
import lombok.Value;

import java.util.Collection;
import java.util.Map;

/**
 *
 */
@Value
public class ExposedAppInfo {
    @Value
    public static class ExposedHost {
        String host;
        int port;
        PortType portType;
    }
    String appId;
    String vhost;
    Map<String, String> tags;
    Collection<ExposedHost> hosts;
}
