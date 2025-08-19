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

package com.phonepe.drove.executor.statemachine.common.actions;

import com.phonepe.drove.common.model.DeploymentUnitSpec;
import com.phonepe.drove.executor.dockerauth.DockerAuthConfig;
import com.phonepe.drove.executor.model.DeployedExecutionObjectInfo;
import com.phonepe.drove.executor.statemachine.ExecutorActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.utils.DockerUtils;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.statemachine.StateData;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 *
 */
@Slf4j
public abstract class CommonExecutableFetchAction<E extends DeployedExecutionObjectInfo, S extends Enum<S>, T extends DeploymentUnitSpec> extends ExecutorActionBase<E, S, T> {

        private final DockerAuthConfig dockerAuthConfig;

    protected CommonExecutableFetchAction(DockerAuthConfig dockerAuthConfig) {
        this.dockerAuthConfig = dockerAuthConfig;
    }

    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<S, E> executeImpl(InstanceActionContext<T> context, StateData<S, E> currentState) {
        val instanceSpec = context.getInstanceSpec();
        val client = context.getClient();
        val image = instanceSpec.getExecutable().accept(DockerCoordinates::getUrl);
        log.info("Pulling docker image: {}", image);
        try {
            return DockerUtils.pullImage(client, dockerAuthConfig, instanceSpec, imageId -> {
                context.setDockerImageId(imageId);
                return StateData.from(currentState, startState());
            });
        }
        catch (InterruptedException e) {
            log.info("Action has been interrupted");
            Thread.currentThread().interrupt();
            return StateData.errorFrom(currentState,
                                       defaultErrorState(),
                                       "Pull operation interrupted");
        }
        catch (Exception e) {
            log.error("Error while pulling image " + image, e);
            return StateData.errorFrom(currentState,
                                       defaultErrorState(),
                                       "Error while pulling image " + image + ": " + e.getMessage());
        }
    }

    protected abstract S startState();

    @Override
    public void stop() {
        //Nothing to do here
    }
}
