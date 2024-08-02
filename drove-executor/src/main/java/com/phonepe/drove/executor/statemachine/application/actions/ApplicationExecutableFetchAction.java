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

package com.phonepe.drove.executor.statemachine.application.actions;

import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.dockerauth.DockerAuthConfig;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.common.actions.CommonExecutableFetchAction;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

/**
 *
 */
@Slf4j
public class ApplicationExecutableFetchAction extends CommonExecutableFetchAction<ExecutorInstanceInfo, InstanceState, ApplicationInstanceSpec> {

    @Inject
    public ApplicationExecutableFetchAction(DockerAuthConfig dockerAuthConfig) {
        super(dockerAuthConfig);
    }

    @Override
    protected InstanceState startState() {
        return InstanceState.STARTING;
    }

    @Override
    protected InstanceState defaultErrorState() {
        return InstanceState.PROVISIONING_FAILED;
    }

    @Override
    protected InstanceState stoppedState() {
        return InstanceState.STOPPED;
    }

    @Override
    public void stop() {
        //Nothing to do here
    }
}
