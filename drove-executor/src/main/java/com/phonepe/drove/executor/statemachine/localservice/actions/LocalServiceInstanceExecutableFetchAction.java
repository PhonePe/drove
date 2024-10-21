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

package com.phonepe.drove.executor.statemachine.localservice.actions;

import com.phonepe.drove.common.model.LocalServiceInstanceSpec;
import com.phonepe.drove.executor.dockerauth.DockerAuthConfig;
import com.phonepe.drove.executor.model.ExecutorLocalServiceInstanceInfo;
import com.phonepe.drove.executor.statemachine.common.actions.CommonExecutableFetchAction;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

/**
 *
 */
@Slf4j
public class LocalServiceInstanceExecutableFetchAction extends CommonExecutableFetchAction<ExecutorLocalServiceInstanceInfo, LocalServiceInstanceState, LocalServiceInstanceSpec> {

    @Inject
    public LocalServiceInstanceExecutableFetchAction(DockerAuthConfig dockerAuthConfig) {
        super(dockerAuthConfig);
    }

    @Override
    protected LocalServiceInstanceState startState() {
        return LocalServiceInstanceState.STARTING;
    }

    @Override
    protected LocalServiceInstanceState defaultErrorState() {
        return LocalServiceInstanceState.PROVISIONING_FAILED;
    }

    @Override
    protected LocalServiceInstanceState stoppedState() {
        return LocalServiceInstanceState.STOPPED;
    }

    @Override
    public final void stop() {
        //Nothing to do here
    }
}
