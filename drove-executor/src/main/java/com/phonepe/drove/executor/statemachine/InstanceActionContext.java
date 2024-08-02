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

package com.phonepe.drove.executor.statemachine;

import com.github.dockerjava.api.DockerClient;
import com.phonepe.drove.common.model.DeploymentUnitSpec;
import com.phonepe.drove.statemachine.ActionContext;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class InstanceActionContext<T extends DeploymentUnitSpec> extends ActionContext<Void> {
    private final String executorId;
    private final T instanceSpec;
    private final DockerClient client;
    private final boolean recovered;
    private String dockerImageId;
    private String dockerInstanceId;
    
    public InstanceActionContext(
            String executorId,
            T instanceSpec,
            DockerClient client,
            boolean recovered) {
        super();
        this.executorId = executorId;
        this.instanceSpec = instanceSpec;
        this.client = client;
        this.recovered = recovered;
    }
}
