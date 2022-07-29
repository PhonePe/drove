package com.phonepe.drove.common.model;

import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.executable.ExecutableCoordinates;
import com.phonepe.drove.models.application.logging.LoggingSpec;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 *
 */
@Value
public class TaskInstanceSpec implements DeploymentUnitSpec {
    String taskId;
    String sourceAppName;
    String instanceId;
    ExecutableCoordinates executable;
    List<ResourceAllocation> resources;
    List<MountedVolume> volumes;
    LoggingSpec loggingSpec;
    Map<String, String> env;


    @Override
    public <T> T accept(DeploymentUnitSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
