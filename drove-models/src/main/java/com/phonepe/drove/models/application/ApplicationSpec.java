package com.phonepe.drove.models.application;

import com.phonepe.drove.models.application.changenotification.StateChangeNotificationSpec;
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
import java.util.List;
import java.util.Map;

/**
 *
 */
@Value
public class ApplicationSpec {
    @NotEmpty
    String name;

    @NotEmpty
    String version;

    @NotNull
    @Valid
    ExecutableCoordinates executable;

    @NotEmpty
    @Valid
    List<PortSpec> exposedPorts;

    @Valid
    List<MountedVolume> volumes;

    @NotNull
    JobType type;

    @Valid
    LoggingSpec logging;

    @Valid
    StateChangeNotificationSpec stateChangeNotificationReceivers;

    @NotEmpty
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
}
