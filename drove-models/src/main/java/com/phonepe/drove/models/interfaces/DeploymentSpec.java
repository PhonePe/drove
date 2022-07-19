package com.phonepe.drove.models.interfaces;

import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.executable.ExecutableCoordinates;
import com.phonepe.drove.models.application.logging.LoggingSpec;
import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;

import java.util.List;
import java.util.Map;

/**
 *
 */
public interface DeploymentSpec {
    String getName();

    String getVersion();

    ExecutableCoordinates getExecutable();

    List<MountedVolume> getVolumes();

    LoggingSpec getLogging();

    List<ResourceRequirement> getResources();

    PlacementPolicy getPlacementPolicy();

    Map<String, String> getTags();

    Map<String, String> getEnv();
}
