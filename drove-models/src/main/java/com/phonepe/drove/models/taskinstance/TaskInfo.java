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

package com.phonepe.drove.models.taskinstance;

import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.executable.ExecutableCoordinates;
import com.phonepe.drove.models.application.logging.LoggingSpec;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfo;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfoVisitor;
import lombok.Value;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 *
 */
@Value
public class TaskInfo implements DeployedInstanceInfo {
    String sourceAppName;
    String taskId;
    String instanceId;
    String executorId;
    String hostname;
    ExecutableCoordinates executable;
    List<ResourceAllocation> resources;
    List<MountedVolume> volumes;
    LoggingSpec loggingSpec;
    Map<String, String> env;
    TaskState state;
    Map<String, String> metadata;
    TaskResult taskResult;
    String errorMessage;
    Date created;
    Date updated;

    @Override
    public <T> T accept(DeployedInstanceInfoVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
