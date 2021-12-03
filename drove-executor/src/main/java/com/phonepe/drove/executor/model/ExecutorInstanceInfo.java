package com.phonepe.drove.executor.model;

import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import lombok.Value;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 *
 */
@Value
public class ExecutorInstanceInfo {
    String appId;
    String appName;
    String instanceId;
    String executorId;
    LocalInstanceInfo localInfo;
    List<ResourceAllocation> resources;
    Map<String, String> metadata;
    Date created;
    Date updated;
}
