package com.phonepe.drove.executor.model;

import com.phonepe.drove.models.instance.LocalInstanceInfo;
import lombok.Value;

import java.util.Date;
import java.util.Map;

/**
 *
 */
@Value
public class ExecutorInstanceInfo {
    String appId;
    String instanceId;
    String executorId;
    LocalInstanceInfo localInfo;
    Map<String, String> metadata;
    Date created;
    Date updated;
}
