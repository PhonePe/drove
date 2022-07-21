package com.phonepe.drove.common.model;

import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.executable.ExecutableCoordinates;
import com.phonepe.drove.models.application.logging.LoggingSpec;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;

import java.util.List;
import java.util.Map;

/**
 *
 */
public interface DeploymentUnitSpec {
    String getInstanceId();
    ExecutableCoordinates getExecutable();
    List<ResourceAllocation> getResources();
    List<MountedVolume> getVolumes();
    LoggingSpec getLoggingSpec();
    Map<String, String> getEnv();

    <T> T accept(final DeploymentUnitSpecVisitor<T> visitor);
}
