package com.phonepe.drove.internalmodels;

import com.phonepe.drove.models.application.AppId;
import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.PortSpec;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.executable.ExecutableCoordinates;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 *
 */
@Value
public class InstanceSpec {
    AppId appId;
    ExecutableCoordinates executable;
    List<ResourceRequirement> resources;
    List<PortSpec> ports;
    List<MountedVolume> volumes;
    CheckSpec healthcheck;
    CheckSpec readiness;
    Map<String, String> env;
}
