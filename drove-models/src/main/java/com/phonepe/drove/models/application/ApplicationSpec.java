package com.phonepe.drove.models.application;

import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.executable.ExecutableCoordinates;
import com.phonepe.drove.models.application.exposure.ExposureSpec;
import com.phonepe.drove.models.application.logging.LoggingSpec;
import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;
import lombok.Value;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;

/**
 *
 */
@Value
public class ApplicationSpec {
    @NotEmpty(message = "- Application name is mandatory")
    @Pattern(regexp = "[a-zA-Z\\d\\-_]*", message = "- Only characters, numbers, hyphen and underscore is allowed")
    String name;

    @NotEmpty(message = "- App version is mandatory")
    @Pattern(regexp = "[a-zA-Z\\d\\-_]*", message = "- Only characters, numbers, hyphen and underscore is allowed")
    String version;

    @NotNull(message = "- Executable details is required")
    @Valid
    ExecutableCoordinates executable;

    @NotEmpty(message = "- Port specifications are needed")
    @Valid
    List<PortSpec> exposedPorts;

    @Valid
    List<MountedVolume> volumes;

    @NotNull(message = "- Specify if job is a computation or a service")
    JobType type;

    @Valid
    LoggingSpec logging;

    @NotEmpty(message = "- CPU/Memory requirements must be specified")
    @Valid
    List<ResourceRequirement> resources;

    @Valid
    PlacementPolicy placementPolicy;

    @NotNull
    @Valid
    CheckSpec healthcheck;

    @NotNull
    @Valid
    CheckSpec readiness;

    Map<String, String> tags;

    Map<String, String> env;

    @Valid
    ExposureSpec exposureSpec;

    @Valid
    PreShutdownSpec preShutdown;
}
