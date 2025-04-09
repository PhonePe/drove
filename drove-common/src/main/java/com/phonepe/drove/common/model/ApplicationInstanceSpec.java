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

package com.phonepe.drove.common.model;

import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.PortSpec;
import com.phonepe.drove.models.application.PreShutdownSpec;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.devices.DeviceSpec;
import com.phonepe.drove.models.application.executable.ExecutableCoordinates;
import com.phonepe.drove.models.application.logging.LoggingSpec;
import com.phonepe.drove.models.application.nonroot.UserSpec;
import com.phonepe.drove.models.config.ConfigSpec;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import lombok.Value;
import lombok.With;

import java.util.List;
import java.util.Map;

/**
 *
 */
@Value
@With
public class ApplicationInstanceSpec implements DeploymentUnitSpec {
    String appId;
    String appName;
    String instanceId;
    ExecutableCoordinates executable;
    List<ResourceAllocation> resources;
    List<PortSpec> ports;
    List<MountedVolume> volumes;
    List<ConfigSpec> configs;
    CheckSpec healthcheck;
    CheckSpec readiness;
    LoggingSpec loggingSpec;
    Map<String, String> env;
    List<String> args;
    List<DeviceSpec> devices;
    PreShutdownSpec preShutdown;
    UserSpec userSpec;
    String instanceAuthToken;

    @Override
    public <T> T accept(DeploymentUnitSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
