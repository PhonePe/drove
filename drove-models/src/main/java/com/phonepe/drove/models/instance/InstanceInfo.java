package com.phonepe.drove.models.instance;

import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfo;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfoVisitor;
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
public class InstanceInfo implements DeployedInstanceInfo {
    String appId;
    String appName;
    String instanceId;
    String executorId;
    LocalInstanceInfo localInfo;
    List<ResourceAllocation> resources;
    InstanceState state;
    Map<String, String> metadata;
    String errorMessage;
    Date created;
    Date updated;

    @Override
    public <T> T accept(DeployedInstanceInfoVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
