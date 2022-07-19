package com.phonepe.drove.models.taskinstance;

import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 *
 */
@Value
@Jacksonized
@Builder
@AllArgsConstructor
public class TaskInstanceInfo {
    String taskId;
    String taskName;
    String instanceId;
    String executorId;
    String hostname;
    List<ResourceAllocation> resources;
    TaskInstanceState state;
    Map<String, String> metadata;
    String errorMessage;
    Date created;
    Date updated;
}
