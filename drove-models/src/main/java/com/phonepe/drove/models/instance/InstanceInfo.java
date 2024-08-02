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

package com.phonepe.drove.models.instance;

import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfo;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfoVisitor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 *
 */
@Value
@Jacksonized
@Builder
@AllArgsConstructor
public class InstanceInfo implements DeployedInstanceInfo {
    String appId;
    String appName;
    String instanceId;
    String executorId;
    LocalInstanceInfo localInfo;
    List<ResourceAllocation> resources;
    InstanceState state;
    Map<String, String> metadata;
    String errorMessage;
    Date created;
    Date updated;

    @Override
    public <T> T accept(DeployedInstanceInfoVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
