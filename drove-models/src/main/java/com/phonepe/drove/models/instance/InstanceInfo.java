package com.phonepe.drove.models.instance;

import lombok.Value;

import java.util.Date;
import java.util.Map;

/**
 *
 */
@Value
public class InstanceInfo {
    String appId;
    String instanceId;
    String executorId;
    String hostname;
    InstanceState state;
    Map<String, InstancePort> ports;
    Map<String, String> metadata;
    Date created;
    Date updated;
}
