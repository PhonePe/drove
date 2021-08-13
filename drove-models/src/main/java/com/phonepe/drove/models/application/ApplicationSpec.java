package com.phonepe.drove.models.application;

import com.phonepe.drove.models.application.changenotification.StateChangeNotificationSpec;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.executable.ExecutableCoordinates;
import com.phonepe.drove.models.application.exposure.ExposureSpec;
import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;
import lombok.Value;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
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

    @Min(0)
    int version;

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
    StateChangeNotificationSpec stateChangeNotificationReceivers;

    @NotEmpty
    @Valid
    List<ResourceRequirement> resources;

    @Valid
    PlacementPolicy placementPolicy;

    @Min(1)
    @Max(2048)
    int instances;

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
