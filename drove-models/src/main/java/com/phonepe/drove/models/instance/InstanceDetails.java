package com.phonepe.drove.models.instance;

import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import lombok.Value;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 *
 */
@Value
public class InstanceDetails {
    String appId;
    String appName;
    String instanceId;
    String executorId;
    String executorHost;
    LocalInstanceInfo localInfo;
    List<ResourceAllocation> resources;
    InstanceState state;
    Map<String, String> metadata;
    Date created;
    Date updated;
}
