/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.models.interfaces;

import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.devices.DeviceSpec;
import com.phonepe.drove.models.application.executable.ExecutableCoordinates;
import com.phonepe.drove.models.application.logging.LoggingSpec;
import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;
import com.phonepe.drove.models.config.ConfigSpec;

import java.util.List;
import java.util.Map;

/**
 *
 */
public interface DeploymentSpec {

    ExecutableCoordinates getExecutable();

    List<MountedVolume> getVolumes();

    LoggingSpec getLogging();

    List<ResourceRequirement> getResources();

    PlacementPolicy getPlacementPolicy();

    Map<String, String> getTags();

    Map<String, String> getEnv();

    List<String> getArgs();

    List<DeviceSpec> getDevices();

    List<ConfigSpec> getConfigs();

    <T> T accept(final DeploymentSpecVisitor<T> visitor);
}
