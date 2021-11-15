package com.phonepe.drove.models.instance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Date;
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
    InstanceState state;
    Map<String, String> metadata;
    Date created;
    Date updated;
}
