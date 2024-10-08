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

package com.phonepe.drove.executor.statemachine.application;

import com.github.dockerjava.api.DockerClient;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.ExecutorActionFactory;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.ExecutorActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.application.actions.*;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.statemachine.StateData;
import com.phonepe.drove.statemachine.StateMachine;
import com.phonepe.drove.statemachine.Transition;
import lombok.NonNull;

import java.util.List;

import static com.phonepe.drove.models.instance.InstanceState.*;

/**
 *
 */
public class ApplicationInstanceStateMachine extends StateMachine<ExecutorInstanceInfo, Void, InstanceState, InstanceActionContext<ApplicationInstanceSpec>, ExecutorActionBase<ExecutorInstanceInfo, InstanceState, ApplicationInstanceSpec>> {
    private static final List<Transition<ExecutorInstanceInfo, Void, InstanceState, InstanceActionContext<ApplicationInstanceSpec>, ExecutorActionBase<ExecutorInstanceInfo, InstanceState, ApplicationInstanceSpec>>> transitions
            = List.of(
            new Transition<>(PENDING,
                             ApplicationInstanceSpecValidator.class,
                             PROVISIONING,
                             STOPPING),
            new Transition<>(PROVISIONING,
                             ApplicationExecutableFetchAction.class,
                             STARTING,
                             PROVISIONING_FAILED),
            new Transition<>(STARTING,
                             ApplicationInstanceRunAction.class,
                             UNREADY,
                             START_FAILED),
            new Transition<>(UNKNOWN,
                             ApplicationInstanceRecoveryAction.class,
                             UNREADY,
                             STOPPED),
            new Transition<>(UNREADY,
                             ApplicationInstanceReadinessCheckAction.class,
                             READY,
                             READINESS_CHECK_FAILED,
                             STOPPING),
            new Transition<>(READY,
                             ApplicationInstanceSingularHealthCheckAction.class,
                             HEALTHY,
                             STOPPING),
            new Transition<>(HEALTHY,
                             ApplicationInstanceHealthcheckAction.class,
                             UNHEALTHY,
                             STOPPING),
            new Transition<>(STOPPING,
                             ApplicationInstanceStopAction.class,
                             DEPROVISIONING),
            new Transition<>(UNHEALTHY,
                             ApplicationInstanceStopAction.class,
                             DEPROVISIONING),
            new Transition<>(PROVISIONING_FAILED,
                             ApplicationInstanceDestroyAction.class,
                             DEPROVISIONING),
            new Transition<>(START_FAILED,
                             ApplicationInstanceDestroyAction.class,
                             DEPROVISIONING),
            new Transition<>(READINESS_CHECK_FAILED,
                             ApplicationInstanceStopAction.class,
                             DEPROVISIONING),
            new Transition<>(DEPROVISIONING,
                             ApplicationExecutableCleanupAction.class,
                             STOPPED));

    public ApplicationInstanceStateMachine(
            String executorId,
            ApplicationInstanceSpec instanceSpec,
            @NonNull StateData<InstanceState, ExecutorInstanceInfo> initalState,
            ExecutorActionFactory<ExecutorInstanceInfo, InstanceState, ApplicationInstanceSpec> actionFactory,
            DockerClient client,
            boolean recovered) {
        super(initalState, new InstanceActionContext<>(executorId, instanceSpec, client, recovered), actionFactory, transitions);
    }
}
