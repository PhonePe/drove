package com.phonepe.drove.models.instance;

import com.phonepe.drove.models.application.AppId;
import lombok.Value;

import java.util.Date;
import java.util.Map;

/**
 *
 */
@Value
public class InstanceInfo {
    AppId appId;
    String instanceId;
    String hostname;
    InstanceState state;
    Map<String, InstancePort> ports;
    Map<String, String> metadata;
    Date created;
    Date updated;
}
