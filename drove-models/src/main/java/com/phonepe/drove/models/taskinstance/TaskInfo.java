package com.phonepe.drove.models.taskinstance;

import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.executable.ExecutableCoordinates;
import com.phonepe.drove.models.application.logging.LoggingSpec;
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
public class TaskInfo implements DeployedInstanceInfo {
    String sourceAppName;
    String taskId;
    String instanceId;
    String executorId;
    String hostname;
    ExecutableCoordinates executable;
    List<ResourceAllocation> resources;
    List<MountedVolume> volumes;
    LoggingSpec loggingSpec;
    Map<String, String> env;
    TaskState state;
    Map<String, String> metadata;
    TaskResult taskResult;
    String errorMessage;
    Date created;
    Date updated;

    @Override
    public <T> T accept(DeployedInstanceInfoVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
