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
    LocalInstanceInfo localInfo;
    InstanceState state;
    Map<String, String> metadata;
    Date created;
    Date updated;

/*    public InstanceInfo updateState(InstanceState state) {
        return new InstanceInfo(appId, instanceId, executorId, hostname, state, ports, metadata, created, new Date());
    }*/
}
