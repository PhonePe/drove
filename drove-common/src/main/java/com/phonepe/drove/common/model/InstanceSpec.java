package com.phonepe.drove.common.model;

import com.phonepe.drove.models.application.checks.CheckModeSpec;
import com.phonepe.drove.models.application.logging.LoggingSpec;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.PortSpec;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.executable.ExecutableCoordinates;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 *
 */
@Value
public class InstanceSpec {
    String appId;
    String appName;
    String instanceId;
    ExecutableCoordinates executable;
    List<ResourceAllocation> resources;
    List<PortSpec> ports;
    List<MountedVolume> volumes;
    CheckSpec healthcheck;
    CheckSpec readiness;
    LoggingSpec loggingSpec;
    Map<String, String> env;
    List<CheckModeSpec> preShutdownHook;
}
