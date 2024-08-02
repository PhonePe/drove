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

package com.phonepe.drove.executor.statemachine.common.actions;

import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.google.common.base.Strings;
import com.phonepe.drove.common.model.DeploymentUnitSpec;
import com.phonepe.drove.executor.ExecutorOptions;
import com.phonepe.drove.executor.model.DeployedExecutionObjectInfo;
import com.phonepe.drove.executor.statemachine.ExecutorActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.utils.DockerUtils;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 *
 */
@Slf4j
public abstract class CommonExecutableCleanupAction<E extends DeployedExecutionObjectInfo, S extends Enum<S>, T extends DeploymentUnitSpec> extends ExecutorActionBase<E, S, T> {

    private final ExecutorOptions options;

    protected CommonExecutableCleanupAction(ExecutorOptions options) {
        this.options = options;
    }



    @Override
    protected StateData<S, E> executeImpl(
            InstanceActionContext<T> context,
            StateData<S, E> currentState) {
        val dockerImageId = context.getDockerImageId();
        if (Strings.isNullOrEmpty(dockerImageId)) {
            log.warn("No docker image id found. Nothing to be cleaned up.");
        }
        else {
            if(options.isCacheImages()) {
                log.info("Skipped image delete for {} as image caching is enabled.", dockerImageId);
            }
            else {
                val dockerClient = context.getClient();
                try {
                    DockerUtils.cleanupImage(dockerClient, dockerImageId);
                }
                catch (NotFoundException e) {
                    log.info("Looks like image {} has already been deleted", dockerImageId);
                }
                catch (ConflictException e) {
                    log.info("Skipping image delete as other containers running with same image {}", dockerImageId);
                }
                catch (Exception e) {
                    log.error("Error trying to cleanup image {}: {}", dockerImageId, e.getMessage());
                    return StateData.create(stoppedState(), currentState.getData(), e.getMessage());
                }
            }
        }
        return StateData.from(currentState, stoppedState());
    }

    @Override
    protected boolean isStopAllowed() {
        return false;
    }

    @Override
    public void stop() {
        //Nothing to do here
    }
}
