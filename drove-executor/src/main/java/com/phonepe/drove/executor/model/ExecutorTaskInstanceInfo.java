package com.phonepe.drove.executor.model;

import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import lombok.Value;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 *
 */
@Value
public class ExecutorTaskInstanceInfo extends DeployedExecutorInstanceInfo {
    String taskId;
    String taskName;
    String instanceId;
    String executorId;
    String hostname;
    List<ResourceAllocation> resources;
    Map<String, String> metadata;
    Date created;
    Date updated;
}
