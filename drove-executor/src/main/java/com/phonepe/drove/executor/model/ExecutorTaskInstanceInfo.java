package com.phonepe.drove.executor.model;

import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.executable.ExecutableCoordinates;
import com.phonepe.drove.models.application.logging.LoggingSpec;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import lombok.Value;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 *
 */
@Value
public class ExecutorTaskInstanceInfo implements DeployedExecutorInstanceInfo {
    String taskId;
    String sourceAppName;
    String instanceId;
    String executorId;
    String hostname;
    ExecutableCoordinates executable;
    List<ResourceAllocation> resources;
    List<MountedVolume> volumes;
    LoggingSpec loggingSpec;
    Map<String, String> env;
    Map<String, String> metadata;
    Date created;
    Date updated;

    @Override
    public <T> T accept(DeployedExecutorInstanceInfoVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
