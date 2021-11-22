package com.phonepe.drove.models.instance;

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
public class InstanceInfo {
    String appId;
    String instanceId;
    String executorId;
    LocalInstanceInfo localInfo;
    List<ResourceAllocation> resources;
    InstanceState state;
    Map<String, String> metadata;
    Date created;
    Date updated;
}
