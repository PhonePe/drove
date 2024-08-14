/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.common.model;

import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.executable.ExecutableCoordinates;
import com.phonepe.drove.models.application.logging.LoggingSpec;
import com.phonepe.drove.models.config.ConfigSpec;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;

import java.util.List;
import java.util.Map;

/**
 *
 */
public interface DeploymentUnitSpec {
    ExecutableCoordinates getExecutable();
    List<ResourceAllocation> getResources();
    List<MountedVolume> getVolumes();
    List<ConfigSpec> getConfigs();
    LoggingSpec getLoggingSpec();
    Map<String, String> getEnv();
    List<String> getArgs();
    <T> T accept(final DeploymentUnitSpecVisitor<T> visitor);
}
